package com.example.cuffedaddon.collar;

import com.example.cuffedaddon.CuffedAddon;
import com.example.cuffedaddon.capability.ModCapabilities;
import com.example.cuffedaddon.config.CuffedAddonServerConfig;
import com.example.cuffedaddon.gamerule.ModGameRules;
import com.example.cuffedaddon.init.ModItems;
import com.example.cuffedaddon.items.ShockCollarItem;
import com.example.cuffedaddon.network.NetworkHandler;
import com.example.cuffedaddon.network.ShockCollarSyncPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Capability registration/attachment, the apply+remove interaction, the
 * per-tick nausea countdown, and death/respawn carry-over for the Shock Collar.
 *
 * <p>The apply/remove click is a HIGHEST-priority
 * {@code PlayerInteractEvent.EntityInteract} listener rather than
 * {@code Item#interactLivingEntity}, matching what {@code PlayerPickerEvents},
 * {@code LiePoseEvents} and {@code WallPoseEvents} already do in this addon and
 * for the same reason: Cuffed's own interaction dispatch (frisking,
 * restraint-removal, escorting) competes for the very same right-click on a
 * player, so winning that race explicitly is safer than finding out the hard
 * way whether {@code interactLivingEntity} is even reached.
 */
@Mod.EventBusSubscriber(modid = CuffedAddon.MODID)
public class ShockCollarEvents {

    public static final ResourceLocation COLLARED_ID =
            ResourceLocation.fromNamespaceAndPath(CuffedAddon.MODID, "collared");

    /**
     * Collar state snapshotted at LivingDeathEvent, keyed by player UUID.
     *
     * <p>This indirection is NOT optional: a dying player's capabilities are
     * invalidated by {@code LivingEntity#die()} long before
     * {@code PlayerEvent.Clone} fires (Clone only happens when the player
     * actually clicks Respawn). Reading the collar capability in Clone's
     * {@code getOriginal()} would therefore come back empty. Both
     * {@code DeathRestraintHandler} and {@code LiePoseCapabilityEvents} already
     * work around this exact pitfall the same way.
     */
    private static final Map<UUID, CompoundTag> PENDING_COLLARS = new HashMap<>();

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(ICollared.class);
    }

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(COLLARED_ID, new CollaredProvider());
        }
    }

    // ------------------------------------------------------- apply / remove

    /**
     * A Shock Collar or a bound remote right-clicked on another player.
     *
     * <h2>This runs on BOTH sides, and that is the whole point (1.5.17)</h2>
     * It used to begin {@code if (!(event.getTarget() instanceof ServerPlayer))
     * return;}, which quietly made it server-only: on a client the local player is
     * a {@code LocalPlayer} and the target an {@code AbstractClientPlayer}, so the
     * handler bailed out before cancelling anything. The server cancelled the
     * interaction; the client never did.
     *
     * <p>That is a real bug, not an inefficiency, because of what the client does
     * with an interaction nobody consumed. {@code Minecraft#startUseItem} only
     * stops at the entity branch when the result {@code consumesAction()};
     * otherwise it falls through and calls {@code Item#use}, sending a use-item
     * packet the server then honours. So crouch + right-click on a collared player
     * with their bound remote did this:
     *
     * <ol>
     *   <li>server: took the collar off them, turning the remote in your hand back
     *       into an unbound Shock Collar;</li>
     *   <li>client: never cancelled, fell through to {@code Item#use};</li>
     *   <li>server: ran {@code ShockCollarItem#use} with the stack now UNBOUND and
     *       you still crouching - which is exactly its self-apply branch, and
     *       <b>collared you with the collar you had just removed</b>.</li>
     * </ol>
     *
     * <p>[stated] hit this the first time they tested with a second player. It
     * could never show up in singleplayer, because {@code EntityInteract} cannot
     * fire on yourself, so nothing ever reached this path there.
     *
     * <p>The fix is for the client to reach the same verdict as the server. The
     * branch is decided from state both sides already have - the stack's own NBT
     * and whether the actor is crouching - and only the state CHANGE is gated
     * behind being server-side.
     *
     * <h2>Why every consuming result here is CONSUME, never SUCCESS</h2>
     * SUCCESS is the only result whose {@code shouldSwing()} is true, and Cuffed
     * self-restrains a player on ANY arm swing while its
     * {@code ALLOW_SELF_RESTRAINING} config is on (it defaults to on), using
     * whatever is in their main hand. This addon's standing rule is therefore to
     * never return SUCCESS from an interact handler. The cost is the arm-swing
     * animation on a successful collar or uncollar; the alternative is a class of
     * bug this project has already paid for twice.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteractWithPlayer(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Player target)) {
            return;
        }
        Player actor = event.getEntity();
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        ItemStack stack = event.getItemStack();
        if (!stack.is(ModItems.SHOCK_COLLAR.get())) {
            return;
        }

        // The only two things the branch depends on, both known on both sides.
        boolean bound = ShockCollarItem.isBound(stack);
        boolean crouching = actor.isShiftKeyDown();

        // Both sides decide the same result below; only the server changes state.
        if (!actor.level().isClientSide()
                && actor instanceof ServerPlayer serverActor
                && target instanceof ServerPlayer serverTarget) {
            act(serverActor, serverTarget, stack, bound, crouching);
        }

        if (bound && !crouching) {
            // Plain click on a bound remote: start shocking. This is the one
            // branch that deliberately does NOT consume - PASS lets the click
            // carry on to Item#use, which is where the held-use shock lives -
            // while cancelling still stops Cuffed's own lower-priority handlers
            // (frisking, escorting, restraint removal) from eating it.
            event.setCancellationResult(InteractionResult.PASS);
        } else {
            // Applying, or crouch-removing. Consume on BOTH sides so the click can
            // never reach Item#use; that fall-through is what used to re-collar
            // the remover.
            event.setCancellationResult(InteractionResult.CONSUME);
        }
        event.setCanceled(true);
    }

    /**
     * The server-side half: actually put the collar on, or take it off.
     *
     * @return whether anything changed. The caller ignores it on purpose: the
     *         cancellation result is the same either way, so a refusal swallows
     *         the click rather than letting it fall through to Item#use and
     *         self-apply.
     */
    private static boolean act(ServerPlayer actor, ServerPlayer target, ItemStack stack,
                               boolean bound, boolean crouching) {
        if (!bound) {
            // Unbound: put the collar on them.
            return ShockCollarUtil.applyCollar(actor, target, stack);
        }
        if (crouching) {
            // Crouching with a bound remote: take the collar off, but only off ITS
            // OWN wearer. A remote is not a spare collar - it's the control for one
            // that already exists, and removeCollar checks that binding.
            return ShockCollarUtil.removeCollar(target, stack);
        }
        // Plain click: the shock starts in Item#use, not here.
        return false;
    }

    // ------------------------------------------------------------ tick loop

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        ShockCollarUtil.tickCollared(player);
    }

    // ----------------------------------------------------- death / respawn

    /**
     * Snapshot at death, while the capability is still valid.
     *
     * <p>Whether the collar survives death follows the addon's existing
     * {@code freeAfterDeath} gamerule, so the collar behaves like every other
     * restraint here rather than inventing a second rule for the same question:
     * gamerule true (the default) frees the player on death, false carries the
     * collar through the respawn.
     *
     * <p>When death DOES free the collar, it settles the pair exactly as a
     * struggle-break would, honouring the same "Drop Item When Broken" config -
     * off, both halves are destroyed; on, the collar drops at the death spot and
     * the bound remote reverts to a plain Shock Remote. Routing both paths
     * through one rule avoids the collar having two different answers to "what
     * happens to the remote when this ends".
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeathCapture(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ICollared cap = ShockCollarUtil.getCollared(player);
        if (cap == null || !cap.isCollared()) {
            return;
        }

        if (isGameRuleEnabled(player, ModGameRules.FREE_AFTER_DEATH)) {
            UUID binding = cap.getBindingId();
            MinecraftServer server = player.getServer();
            boolean salvage = CuffedAddonServerConfig.SHOCK_COLLAR_DROP_ITEM_WHEN_BROKEN.get();
            if (binding != null && server != null) {
                ShockCollarUtil.revokeBinding(server, binding, salvage);
            }
            if (salvage) {
                ShockCollarUtil.dropUnboundCollar(player);
            }
            return;
        }

        PENDING_COLLARS.put(player.getUUID(), cap.serializeNBT());
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer newPlayer)) {
            return;
        }

        if (event.isWasDeath()) {
            CompoundTag snapshot = PENDING_COLLARS.remove(newPlayer.getUUID());
            if (snapshot != null) {
                newPlayer.getCapability(ModCapabilities.COLLARED)
                        .ifPresent(cap -> cap.deserializeNBT(snapshot));
            }
            return;
        }

        // Non-death clone (dimension change / return from End): the original
        // entity's capabilities are still valid here, so copy directly.
        event.getOriginal().getCapability(ModCapabilities.COLLARED).ifPresent(oldCap ->
                newPlayer.getCapability(ModCapabilities.COLLARED).ifPresent(newCap ->
                        newCap.deserializeNBT(oldCap.serializeNBT())));
    }

    /** Push collar state to the client on join/respawn/dimension change, for the HUD. */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ShockCollarUtil.sync(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ShockCollarUtil.sync(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ShockCollarUtil.sync(player);
        }
    }

    /**
     * A player who walks into range of an already-collared player missed the
     * broadcast that set that collar, so send them the current state now.
     * Without this the collar would simply be invisible to anyone who wasn't
     * already nearby when it was applied.
     */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof ServerPlayer target)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer watcher)) {
            return;
        }
        ICollared cap = ShockCollarUtil.getCollared(target);
        if (cap == null || !cap.isCollared()) {
            return;
        }
        NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> watcher),
                new ShockCollarSyncPacket(target.getId(), true, cap.getDurability(),
                        ShockCollarUtil.maxDurability()));
    }

    private static boolean isGameRuleEnabled(ServerPlayer player, GameRules.Key<GameRules.BooleanValue> key) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return true;
        }
        return serverLevel.getGameRules().getBoolean(key);
    }
}
