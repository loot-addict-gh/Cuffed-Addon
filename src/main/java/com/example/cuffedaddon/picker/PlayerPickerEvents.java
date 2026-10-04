package com.example.cuffedaddon.picker;

import com.example.cuffedaddon.CuffedAddon;
import com.example.cuffedaddon.capability.ModCapabilities;
import com.example.cuffedaddon.init.ModItems;
import com.example.cuffedaddon.items.PlayerPickerItem;
import com.example.cuffedaddon.network.NetworkHandler;
import com.example.cuffedaddon.network.PlayerPickedSelfSyncPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * The PICK action is a HIGHEST-priority PlayerInteractEvent.EntityInteract
 * listener rather than a plain Item#interactLivingEntity override - same
 * reasoning LiePoseEvents/WallPoseEvents already documented for their own
 * onInteractWithPosedTarget: the TARGET here is (by this item's own
 * precondition) always arm+leg restrained, meaning Cuffed's own
 * interaction dispatch (frisking, restraint-removal, etc.) is already
 * competing for the exact same click. Winning that race with HIGHEST
 * priority, same as the existing pattern, avoids needing to find out the
 * hard way whether Item#interactLivingEntity would even get reached.
 */
@Mod.EventBusSubscriber(modid = CuffedAddon.MODID)
public class PlayerPickerEvents {

    public static final ResourceLocation PLAYER_PICKED_ID =
            ResourceLocation.fromNamespaceAndPath(CuffedAddon.MODID, "player_picked");

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(IPlayerPicked.class);
    }

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(PLAYER_PICKED_ID, new PlayerPickedProvider());
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            // A picked player is fully immobilized/spectator - shouldn't be
            // able to die in the first place, but clear defensively rather
            // than ever carry a stuck "picked" flag across a real death.
            event.getEntity().getCapability(ModCapabilities.PLAYER_PICKED)
                    .ifPresent(newCap -> newCap.setPicked(false));
            if (event.getEntity() instanceof ServerPlayer respawned) {
                PlayerPickerUtil.dismountAndDiscardAnchor(respawned);
                NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> respawned),
                        new PlayerPickedSelfSyncPacket(false));
            }
            return;
        }

        event.getOriginal().getCapability(ModCapabilities.PLAYER_PICKED).ifPresent(oldCap ->
                event.getEntity().getCapability(ModCapabilities.PLAYER_PICKED).ifPresent(newCap ->
                        newCap.deserializeNBT(oldCap.serializeNBT())));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteractWithRestrainedTarget(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof ServerPlayer target)) return;
        if (!(event.getEntity() instanceof ServerPlayer actor)) return;
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;

        ItemStack stack = event.getItemStack();
        if (!stack.is(ModItems.PLAYER_PICKER.get())) return;

        boolean handled = PlayerPickerUtil.tryPick(actor, target, stack);
        event.setCancellationResult(handled ? InteractionResult.SUCCESS : InteractionResult.FAIL);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            IPlayerPicked cap = PlayerPickerUtil.get(player);
            if (cap != null && cap.isPicked()) {
                PlayerPickerUtil.tickPickedPlayer(server, player, cap);
            }
        }
    }

    /**
     * [stated]'s explicit spec: logging off with a captured player still in
     * your OWN inventory auto-releases them in place (restores gamemode,
     * doesn't move them) - the only way to keep someone picked across a
     * logout is to leave them in a container instead. Only scans the
     * logging-off player's OWN inventory (a container-anchored capture
     * survives its owner's logout untouched, by design - see this class's
     * own doc and /areas/cuffedaddon.md).
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer owner)) return;

        for (ItemStack stack : owner.getInventory().items) {
            releaseIfHeldBy(stack);
        }
        for (ItemStack stack : owner.getInventory().offhand) {
            releaseIfHeldBy(stack);
        }
    }

    private static void releaseIfHeldBy(ItemStack stack) {
        if (!PlayerPickerItem.hasPickedPlayer(stack)) return;
        var profile = PlayerPickerItem.getPickedProfile(stack);
        if (profile == null) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        // A profile written by hand or by another mod can be missing its Id (the
        // same NBT that used to crash the renderer - see PlayerPickerItemRenderer
        // in 1.4.40). getPlayer(null) is not a lookup worth attempting.
        if (profile.getId() == null) return;

        ServerPlayer picked = server.getPlayerList().getPlayer(profile.getId());
        if (picked == null) return; // shouldn't happen - only online players get ticked/anchored

        // Shared release path as of 1.4.40 - see PlayerPickerUtil#releasePicked.
        PlayerPickerUtil.releasePicked(picked);
        PlayerPickerItem.clearPickedPlayer(stack);
    }

    /**
     * Enforces "[stated] a loaded Player Picker cannot go in an Ender Chest"
     * WITHOUT Mixin. An earlier attempt injected @Inject into
     * Container#canPlaceItem (a default interface method) via a Mixin
     * targeting the Container interface itself - that compiled fine but
     * crashed the game at mod-loading bootstrap
     * (InvalidInterfaceMixinException), because Mixin 0.8.5 does not support
     * @Inject callback injection into an interface's own default method.
     * That is a hard capability limit of this Mixin version, not a
     * location/naming mistake, so the whole Mixin-based approach was
     * abandoned (see /areas/cuffedaddon.md).
     * <p>
     * This replacement follows the same "let it happen, then undo it"
     * convention already established elsewhere in this codebase (see
     * LiePoseEvents' self-restraint-on-swing guard): rather than blocking
     * the insert up front, it reacts to PlayerContainerEvent.Close - fired
     * whenever a player closes ANY container screen - by scanning that
     * player's ender chest inventory for a loaded Player Picker and, if one
     * is found, evicting it straight back into the player's own inventory
     * (or dropping it at their feet if their inventory is full). The scan
     * runs on every container close rather than trying to detect
     * specifically the ender chest menu, since scanning 27 slots is trivial
     * and this way there's no dependency on ChestMenu's internal container
     * accessor.
     */
    @SubscribeEvent
    public static void onContainerClosed(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        evictLoadedPickersFromEnderChest(player);
    }

    private static void evictLoadedPickersFromEnderChest(ServerPlayer player) {
        var enderChest = player.getEnderChestInventory();
        for (int i = 0; i < enderChest.getContainerSize(); i++) {
            ItemStack stack = enderChest.getItem(i);
            if (!PlayerPickerItem.hasPickedPlayer(stack)) continue;

            enderChest.setItem(i, ItemStack.EMPTY);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }
}
