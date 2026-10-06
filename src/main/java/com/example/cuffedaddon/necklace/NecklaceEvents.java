package com.example.cuffedaddon.necklace;

import com.example.cuffedaddon.CuffedAddon;
import com.example.cuffedaddon.capability.ModCapabilities;
import com.example.cuffedaddon.fakeplayer.FakePlayerSupport;
import com.example.cuffedaddon.init.ModItems;
import com.example.cuffedaddon.network.NecklaceSyncPacket;
import com.example.cuffedaddon.network.NetworkHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
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
 * Capability registration/attachment, the right-click apply and crouch +
 * right-click removal, death/respawn carry-over and the three sync sends for
 * the Key Necklace.
 *
 * <p>The SELF gestures - plain left-click to put one on, crouch + left-click to
 * take one off - are NOT here. A left click is an arm swing, not an interact
 * event, and {@code PlayerInteractEvent.EntityInteract} can never fire on
 * yourself anyway; both live in {@code interact.SelfGestureEvents}, which is
 * also what gives the necklace priority over Cuffed's own empty-handed
 * self-removal gesture.
 *
 * <h2>Why the necklace wins the crouch + right-click gesture</h2>
 * [stated]: <i>"it should take priority over any other restraints. crouch +
 * rclick on another player should first remove the Key Necklace and later the
 * non keyed restraint on them."</i>
 *
 * <p>That falls out of this listener's priority and of what it refuses to do,
 * rather than needing any ordering machinery. Cuffed's own
 * {@code ModServerEvents#playerInteractEntity} - the listener that reaches
 * {@code RestrainableCapability#onInteractedByOther} and so removes a non-keyed
 * restraint from an empty-handed crouching player - runs at
 * {@code EventPriority.HIGH}. This one runs at HIGHEST, so it sees the click
 * first:
 *
 * <ul>
 *   <li>target IS wearing a necklace: take it, consume the click, cancel. The
 *       restraint is untouched because Cuffed's listener never runs - a
 *       cancelled event is not delivered to listeners that did not ask for
 *       cancelled events, and none of these do.</li>
 *   <li>target is NOT wearing one: return WITHOUT cancelling, so the very same
 *       gesture falls through to Cuffed and removes the restraint as it always
 *       did.</li>
 * </ul>
 *
 * <p>So it is two clicks: necklace first, restraint second. <b>Illusion needs
 * no special case.</b> An Illusion arm restraint is still a real non-keyed
 * restraint in Cuffed's capability - Illusion only changes what a restraint
 * takes away and how it draws, never whether it is there - so the ordering
 * above applies to it unchanged.
 */
@Mod.EventBusSubscriber(modid = CuffedAddon.MODID)
public class NecklaceEvents {

    public static final ResourceLocation NECKLACED_ID =
            ResourceLocation.fromNamespaceAndPath(CuffedAddon.MODID, "necklaced");

    /**
     * Necklace state snapshotted at {@code LivingDeathEvent}, keyed by player
     * UUID.
     *
     * <p>Same indirection, for the same non-optional reason, as
     * {@code ShockCollarEvents#PENDING_COLLARS}: a dying player's capabilities
     * are invalidated by {@code LivingEntity#die()} long before
     * {@code PlayerEvent.Clone} fires, so reading the capability off
     * {@code getOriginal()} would come back empty.
     */
    private static final Map<UUID, CompoundTag> PENDING_NECKLACES = new HashMap<>();

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(INecklaced.class);
    }

    /**
     * Players, and Fake Players' fake players.
     *
     * <p>[stated] asked for the necklace to go on a fake player too, purely as a
     * cosmetic in its own slot. That needed no parallel representation of the kind
     * the 1.5.x Fake Players round had to build for Cuffed's restraints, and the
     * reason is worth stating: Cuffed is {@code Player}/{@code ServerPlayer}-typed
     * from its capability attach all the way down to {@code AbstractRestraint}'s
     * own method signatures, whereas {@link INecklaced} holds one worn
     * {@code ItemStack} and asks nothing of its carrier. So the SAME capability,
     * the same provider and the same sync packet simply go on their entity as
     * well.
     */
    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player || FakePlayerSupport.isFakePlayer(event.getObject())) {
            event.addCapability(NECKLACED_ID, new NecklacedProvider());
        }
    }

    // ------------------------------------------------------- apply / remove

    /**
     * Right-click with a Key Necklace to put one on someone; crouch +
     * right-click with an EMPTY hand to take one off them.
     *
     * <p><b>This runs on both logical sides deliberately</b>, and the branch is
     * decided only from state both sides have - what is in the actor's hand,
     * whether they are crouching, and whether the target is wearing a necklace
     * (which {@link NecklaceSyncPacket} has already mirrored onto this client's
     * copy of the target). Only the state change is gated on being server-side.
     * The alternative is the 1.5.17 bug class written up at length on
     * {@code ShockCollarEvents#onInteractWithPlayer}: a handler that bails out
     * early on the client leaves the interaction unconsumed there, and
     * {@code Minecraft#startUseItem} then falls through to {@code Item#use} and
     * sends a use-item packet the server honours.
     *
     * <p>Every consuming result is CONSUME, never SUCCESS. SUCCESS is the only
     * result whose {@code shouldSwing()} is true, and Cuffed self-restrains a
     * player on ANY arm swing while its {@code ALLOW_SELF_RESTRAINING} config is
     * on (it defaults to on) - and now that this addon reads the swing itself
     * for the self gestures, a spurious swing would also be read as a left
     * click. The standing rule holds: never SUCCESS from an interact handler in
     * this addon.
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
        boolean applying = stack.is(ModItems.KEY_NECKLACE.get());
        // Empty hand + crouching is Cuffed's own gesture for removing a
        // non-keyed restraint, which is exactly the one asked for here (the
        // necklace is not keyed and not a restraint). See this class's doc for
        // how the two share it without either losing.
        boolean removing = stack.isEmpty() && actor.isShiftKeyDown();

        if (!applying && !removing) {
            return;
        }

        boolean wearing = NecklaceUtil.isWearing(target);
        if (applying && wearing) {
            // Slot already occupied. Let the click go on its way rather than
            // swallowing it - there is nothing here to do with it.
            return;
        }
        if (removing && !wearing) {
            // NOT cancelling is the whole priority mechanism: this is the click
            // that should reach Cuffed and take a non-keyed restraint off.
            return;
        }

        if (!actor.level().isClientSide()
                && actor instanceof ServerPlayer serverActor
                && target instanceof ServerPlayer serverTarget) {
            if (applying) {
                NecklaceUtil.applyNecklace(serverActor, serverTarget, stack);
            } else {
                NecklaceUtil.removeNecklace(serverActor, serverTarget);
            }
        }

        event.setCancellationResult(InteractionResult.CONSUME);
        event.setCanceled(true);
    }

    // ----------------------------------------------------- death / respawn

    /**
     * Snapshot at death, while the capability is still valid.
     *
     * <p><b>This follows vanilla's {@code keepInventory}, NOT this addon's
     * {@code freeAfterDeath}.</b> 1.6.5 first wired it to {@code freeAfterDeath}
     * for consistency with the Shock Collar, and [stated] corrected it: that
     * gamerule is about whether RESTRAINTS survive dying, and a necklace is not a
     * restraint - it is a piece of equipment with a key in it. So it behaves like
     * everything else in the player's inventory. Dying always drops it, unless
     * {@code keepInventory} is on, in which case it stays worn through the
     * respawn.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeathCapture(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            // A fake player that dies wearing one drops it where it fell. No
            // gamerule question to ask: keepInventory is about a PLAYER's
            // inventory, and a fake player does not respawn, so the only
            // alternative would be destroying the item.
            if (FakePlayerSupport.isFakePlayer(event.getEntity())
                    && !event.getEntity().level().isClientSide()) {
                NecklaceUtil.dropOnDeath(event.getEntity());
            }
            return;
        }
        INecklaced cap = NecklaceUtil.get(player);
        if (cap == null || !cap.isWearing()) {
            return;
        }

        if (isGameRuleEnabled(player, GameRules.RULE_KEEPINVENTORY)) {
            PENDING_NECKLACES.put(player.getUUID(), cap.serializeNBT());
            return;
        }

        NecklaceUtil.dropOnDeath(player);
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer newPlayer)) {
            return;
        }

        if (event.isWasDeath()) {
            CompoundTag snapshot = PENDING_NECKLACES.remove(newPlayer.getUUID());
            if (snapshot != null) {
                newPlayer.getCapability(ModCapabilities.NECKLACED)
                        .ifPresent(cap -> cap.deserializeNBT(snapshot));
            }
            return;
        }

        // Non-death clone (dimension change / return from the End): the
        // original entity's capabilities are still valid here, so copy direct.
        event.getOriginal().getCapability(ModCapabilities.NECKLACED).ifPresent(oldCap ->
                newPlayer.getCapability(ModCapabilities.NECKLACED).ifPresent(newCap ->
                        newCap.deserializeNBT(oldCap.serializeNBT())));
    }

    // -------------------------------------------------------------- syncing

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            NecklaceUtil.sync(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            NecklaceUtil.sync(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            NecklaceUtil.sync(player);
        }
    }

    /**
     * A player who walks into range of someone already wearing a necklace
     * missed the broadcast that put it on, so send them the current state now.
     * Without this the necklace is simply invisible to anyone who was not
     * already nearby - the third of the three sends this addon's standing rule
     * requires for render-affecting state.
     */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        // LivingEntity, not ServerPlayer: a fake player is tracked the same way
        // and needs the same resend.
        if (!(event.getTarget() instanceof net.minecraft.world.entity.LivingEntity target)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer watcher)) {
            return;
        }
        if (!NecklaceUtil.isWearing(target)) {
            return;
        }
        NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> watcher),
                new NecklaceSyncPacket(target.getId(), true));
    }

    private static boolean isGameRuleEnabled(ServerPlayer player, GameRules.Key<GameRules.BooleanValue> key) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return true;
        }
        return serverLevel.getGameRules().getBoolean(key);
    }
}
