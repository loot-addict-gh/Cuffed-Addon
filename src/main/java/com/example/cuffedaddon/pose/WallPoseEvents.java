package com.example.cuffedaddon.pose;

import com.example.cuffedaddon.CuffedAddon;
import com.lazrproductions.cuffed.api.CuffedAPI;
import com.lazrproductions.cuffed.cap.RestrainableCapability;
import com.lazrproductions.cuffed.items.base.AbstractRestraintKeyItem;
import com.lazrproductions.cuffed.restraints.RestraintAPI;
import com.lazrproductions.cuffed.restraints.base.AbstractHeadRestraint;
import com.lazrproductions.cuffed.restraints.base.AbstractRestraint;
import com.lazrproductions.cuffed.restraints.base.RestraintType;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.network.PacketDistributor;
import com.example.cuffedaddon.network.NetworkHandler;
import com.example.cuffedaddon.network.WallPoseSyncPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Locks a wall-posed player's position every tick and blocks their own
 * actions, the same re-pin-every-tick technique LiePoseEvents uses. No
 * bounding-box work here at all (unlike LiePoseEvents) - the whole point of
 * this pose is that the hitbox never changes.
 *
 * Release: another player using Cuffed's own Handcuffs Key on the
 * restrained player, same mechanism Bed Restraint uses (see
 * onInteractWithPosedTarget) - "no way of unrestraining yourself unless
 * another player does it or you die".
 *
 * Other-restraint interaction rules mirror Bed Restraint's own
 * (onInteractWithPosedTarget below): arm/leg restraints can't be equipped
 * on a wall-posed target at all (same precondition already checked in
 * WallPoseUtil.tryApply for ENTERING the pose, now also enforced for
 * anyone trying to equip one WHILE already posed) - for the same reason
 * Bed Restraint settled on after its own round-19 revert: this pose has
 * its own always-on cosmetic handcuffs layer (WallPoseHandcuffsLayer,
 * never gated on a real Cuffed restraint) representing "arms/legs
 * restrained" already, and a real Cuffed restraint render layer would
 * draw a second, visually conflicting set of cuffs on top of it. Head
 * restraints have no such conflict and work normally - can be equipped
 * and removed while wall-posed, same as Bed Restraint allows.
 *
 * ALL THREE mechanisms Bed Restraint has that Wall Restraint originally
 * lacked are now ported (this session), verbatim from LiePoseEvents -
 * [stated] hit all three as real bugs during 1.4.14 testing:
 *  - Self-restraint guard (PENDING_SELF_RESTRAINT_GUARD/SWINGING_FIELD):
 *    was scoped out on the theory that this pose's block-right-click
 *    restrain trigger (unlike Bed Restraint's EntityInteract one) would be
 *    less likely to hit Cuffed's swing-triggered self-restrain side
 *    effect - confirmed wrong, it fires here too via the actor's
 *    subsequent EntityInteract with the now-wall-posed target (e.g.
 *    equipping/removing a head restraint), so the guard is wired the same
 *    way, for every player each tick, not just wall-posed ones.
 *  - Item-toss block (onItemToss): identical mechanism, ported directly.
 *  - Possessions Box (frisking), with Curios integration: same gap Bed
 *    Restraint worked around (Cuffed's own frisking listeners only fire
 *    when targetCap.armsRestrained() is true, never true for a pose-locked
 *    target) - see onInteractWithPosedTarget below.
 *
 * Still an open question, NOT investigated yet: [stated] noticed the
 * self-restrain bug doesn't happen for the two combo Sleep Mask restraints
 * specifically, only the plain ones - unclear whether that's pose-specific
 * or a property of those restraint items themselves (same gap noted for
 * Bed Restraint, never chased down there either).
 */
@Mod.EventBusSubscriber(modid = CuffedAddon.MODID)
public class WallPoseEvents {

    /** See LiePoseEvents' own doc on this same field - identical mechanism, ported verbatim. */
    private static final Map<UUID, int[]> PENDING_SELF_RESTRAINT_GUARD = new HashMap<>();
    private static final int SELF_RESTRAINT_GUARD_TICKS = 2;

    /** See LiePoseEvents' own doc on this same field - identical mechanism, ported verbatim. */
    private static final java.lang.reflect.Field SWINGING_FIELD;

    static {
        java.lang.reflect.Field field;
        try {
            // Real installed (production) Forge: SRG-obfuscated name.
            field = net.minecraft.world.entity.LivingEntity.class.getDeclaredField("f_20911_");
        } catch (NoSuchFieldException srgFailed) {
            try {
                // Gradle dev run (runClient/runServer): official/dev-mapped name.
                field = net.minecraft.world.entity.LivingEntity.class.getDeclaredField("swinging");
            } catch (NoSuchFieldException officialFailed) {
                field = null;
            }
        }
        if (field != null) {
            field.setAccessible(true);
        }
        SWINGING_FIELD = field;
    }

    private static void suppressSpuriousSwing(ServerPlayer player) {
        if (SWINGING_FIELD == null) return;

        int[] guard = PENDING_SELF_RESTRAINT_GUARD.get(player.getUUID());
        if (guard == null || guard[2] != SELF_RESTRAINT_GUARD_TICKS) return;

        try {
            SWINGING_FIELD.set(player, false);
        } catch (IllegalAccessException ignored) {
            // Leave it - the Phase.END safety net still cleans up after the fact.
        }
    }

    private static void consumeSelfRestraintGuard(ServerPlayer player) {
        int[] guard = PENDING_SELF_RESTRAINT_GUARD.get(player.getUUID());
        if (guard == null) return;

        RestrainableCapability cap =
                (RestrainableCapability) CuffedAPI.Capabilities.getRestrainableCapability(player);
        if (cap != null) {
            if (guard[0] == 0 && cap.armsRestrained()) {
                cap.TryUnequipRestraint(player, player, RestraintType.Arm);
            }
            if (guard[1] == 0 && cap.legsRestrained()) {
                cap.TryUnequipRestraint(player, player, RestraintType.Leg);
            }
        }

        guard[2]--;
        if (guard[2] <= 0) {
            PENDING_SELF_RESTRAINT_GUARD.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Player tickPlayer = event.player;
        if (tickPlayer.level().isClientSide) return;

        // Checked for EVERY player, not just wall-posed ones - same as
        // LiePoseEvents, this is about the ACTOR, who is never posed.
        if (tickPlayer instanceof ServerPlayer serverPlayer) {
            if (event.phase == TickEvent.Phase.START) {
                suppressSpuriousSwing(serverPlayer);
            } else if (event.phase == TickEvent.Phase.END) {
                consumeSelfRestraintGuard(serverPlayer);
            }
        }

        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;

        IWallPose cap = WallPoseUtil.get(player);
        if (cap == null || !cap.isPosed()) return;

        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        player.setShiftKeyDown(false);
        player.setSprinting(false);
        player.setYBodyRot(cap.getLockedYaw());

        double dx = cap.getLockedX() - player.getX();
        double dy = cap.getLockedY() - player.getY();
        double dz = cap.getLockedZ() - player.getZ();
        boolean drifted = (dx * dx + dy * dy + dz * dz) > 1.0E-4;

        if (drifted) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.connection.teleport(cap.getLockedX(), cap.getLockedY(), cap.getLockedZ(),
                        serverPlayer.getYRot(), serverPlayer.getXRot());
            } else {
                player.setPos(cap.getLockedX(), cap.getLockedY(), cap.getLockedZ());
            }
        }

        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }

        if (player instanceof ServerPlayer serverPlayer && serverPlayer.getInventory().selected != cap.getLockedSlot()) {
            serverPlayer.getInventory().selected = cap.getLockedSlot();
            serverPlayer.connection.send(new ClientboundSetCarriedItemPacket(cap.getLockedSlot()));
        }
    }

    @SubscribeEvent
    public static void onEntityInteractSelfBlock(PlayerInteractEvent.EntityInteract event) {
        if (WallPoseUtil.isPosed(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() != null && WallPoseUtil.isPosed(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (WallPoseUtil.isPosed(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (WallPoseUtil.isPosed(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /**
     * Blocks dropping items (Q / Ctrl+Q) while wall-posed - ported verbatim
     * from LiePoseEvents.onItemToss, same reasoning (Player#drop already
     * removes the item from inventory before this event fires, so it has
     * to be added back before cancelling, not just cancelled outright).
     */
    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        Player player = event.getPlayer();
        if (!WallPoseUtil.isPosed(player)) return;

        player.getInventory().add(event.getEntity().getItem());
        event.setCanceled(true);
    }

    /**
     * Release path (Handcuffs Key) plus the other-restraint interaction
     * rules described in this class's own doc above. Structure and the
     * head-restraint equip/unequip calls are mirrored directly from
     * cuffedaddon's own LiePoseEvents.onInteractWithPosedTarget - same
     * direct-call-into-RestrainableCapability pattern (bypassing Cuffed's
     * own height/crouch-based dispatch entirely, cast to the concrete
     * class the same way, since TryEquipRestraint/TryUnequipRestraint/
     * getHeadRestraint are only public there, not on the interface) rather
     * than re-deriving it independently.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteractWithPosedTarget(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof ServerPlayer target)) return;
        if (!WallPoseUtil.isPosed(target)) return;
        if (!(event.getEntity() instanceof ServerPlayer actor)) return;
        if (WallPoseUtil.isPosed(actor)) return;

        if (event.getHand() != InteractionHand.MAIN_HAND) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
            return;
        }

        // See this class's own doc + LiePoseEvents' PENDING_SELF_RESTRAINT_GUARD
        // doc - snapshot the actor's own arm/leg-restrained state before
        // letting this interaction (and the arm-swing animation that comes
        // with it) happen, regardless of which branch below ends up running.
        RestrainableCapability actorCap =
                (RestrainableCapability) CuffedAPI.Capabilities.getRestrainableCapability(actor);
        PENDING_SELF_RESTRAINT_GUARD.put(actor.getUUID(), new int[]{
                actorCap.armsRestrained() ? 1 : 0,
                actorCap.legsRestrained() ? 1 : 0,
                SELF_RESTRAINT_GUARD_TICKS
        });

        ItemStack stack = event.getItemStack();
        RestrainableCapability targetCap =
                (RestrainableCapability) CuffedAPI.Capabilities.getRestrainableCapability(target);

        // SHOCK COLLAR - handed over to ShockCollarEvents, NOT cancelled here.
        //
        // [stated] found this at 1.6.3: you can collar a player held in a
        // pillory or a Bed Restraint, but not one on a Wall Restraint. The cause
        // is the catch-all at the bottom of this method. This handler predates
        // the Shock Collar by eighteen versions (Wall Restraint merged at
        // 1.4.14, the collar arrived at 1.4.32) and was never given a carve-out
        // for it, so the collar fell into "anything else" and was refused.
        //
        // The asymmetry they noticed is exactly that: LiePoseEvents' equivalent
        // method ENDS by falling through - "anything else (frisking, anchoring,
        // etc) - left alone for Cuffed's own listener" - while this one
        // deliberately blocks everything it does not recognise (see this class's
        // own doc for why anchoring and escorting are refused on a wall-posed
        // target). A pillory is Cuffed's own feature and never passes through
        // here at all. So the bed worked, the pillory worked, and the wall did
        // not.
        //
        // Returning rather than handling it keeps the collar's logic in one
        // place. It is also order-independent: ShockCollarEvents listens at
        // HIGHEST too, and whichever of the two runs second picks this up,
        // because the one that handles a Shock Collar always cancels. And
        // nothing leaks past: every branch of that listener ends in
        // setCanceled(true), so Cuffed's own frisking/escort/anchoring handlers
        // never see this click either way - which is what the catch-all below
        // exists to guarantee.
        if (stack.is(com.example.cuffedaddon.init.ModItems.SHOCK_COLLAR.get())) {
            return;
        }

        // KEY NECKLACE (1.6.5) - handed over to NecklaceEvents, same carve-out
        // and for the same reason as the Shock Collar above. The general rule
        // recorded after the 1.6.3 collar bug is that ANY new item meant to
        // work on a held player needs one of these, or it silently fails on
        // wall-restrained targets only (this method blocks every right-click it
        // does not recognise; LiePoseEvents' equivalent falls through instead,
        // which is why the bed would have worked and the wall would not).
        //
        // Putting a necklace ON a helpless target is very much wanted, and so
        // is taking one OFF - see the crouch + empty-hand branch below, which
        // handles the removal side.
        if (stack.is(com.example.cuffedaddon.init.ModItems.KEY_NECKLACE.get())) {
            return;
        }

        if (stack.is(com.lazrproductions.cuffed.init.ModItems.HANDCUFFS_KEY.get())) {
            WallPoseUtil.release(target);
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }

        // Possessions Box (frisking), with Curios integration - ported from
        // LiePoseEvents.onInteractWithPosedTarget. Same gap this addon
        // worked around there: Cuffed's own frisking listener (and the
        // Curios-aware one) only fire when targetCap.armsRestrained() is
        // true, but a wall-posed target is incapacitated via this addon's
        // OWN pose lock, not Cuffed's arm-restraint capability, so those
        // listeners silently do nothing for our targets. Handled directly
        // via the same public API they themselves call.
        if (stack.is(com.lazrproductions.cuffed.init.ModItems.POSSESSIONSBOX.get())) {
            if (ModList.get().isLoaded("curios")) {
                com.example.cuffedaddon.curios.CurioFriskCompat.frisk(actor, target, stack);
            } else {
                com.lazrproductions.cuffed.items.PossessionsBox.frisk(actor, target, stack);
            }
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }

        boolean isHeadRestraintItem = RestraintAPI.IsHeadRestraintItem(stack)
                // An "empty" Bundle is still a real Bundle you're holding,
                // not an empty ItemStack, and isn't in Cuffed's per-item
                // restraint registry either - matches
                // AbstractRestraintItem.dispenseRestraint's own condition.
                || (stack.is(Items.BUNDLE) && BundleItem.getFullnessDisplay(stack) <= 0);

        if (isHeadRestraintItem) {
            AbstractRestraint restraint = RestraintAPI.getRestraintFromStack(stack, RestraintType.Head, target, actor);
            boolean handled = restraint instanceof AbstractHeadRestraint headRestraint
                    && targetCap.TryEquipRestraint(target, actor, headRestraint);
            if (handled) {
                com.lazrproductions.cuffed.init.ModStatistics.awardRestraintItemUsed(actor, stack);
                stack.shrink(1); // matches Cuffed's own item-consumption behavior on equip
            }
            event.setCancellationResult(handled ? InteractionResult.SUCCESS : InteractionResult.FAIL);
            event.setCanceled(true);
            return;
        }

        if (RestraintAPI.IsArmRestraintItem(stack) || RestraintAPI.IsLegRestraintItem(stack)) {
            // Can't be equipped on a wall-posed target - see this class's
            // own doc for why (visual conflict with the always-on cosmetic
            // handcuffs layer).
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
            return;
        }

        if (stack.getItem() instanceof AbstractRestraintKeyItem) {
            boolean handled = tryUnequipHeadKeyed(targetCap, target, actor, stack);
            event.setCancellationResult(handled ? InteractionResult.SUCCESS : InteractionResult.FAIL);
            event.setCanceled(true);
            return;
        }

        if (stack.isEmpty() && actor.isCrouching()) {
            // KEY NECKLACE FIRST (1.6.5), per [stated]: the necklace takes
            // priority over any restraint on the same crouch + empty-hand
            // gesture, so a captor lifts the key off their neck before starting
            // on anything else. Done HERE rather than left to NecklaceEvents
            // because both listeners sit at HIGHEST and the order between two
            // same-priority listeners is registration order - whereas this
            // branch and NecklaceEvents want the very same click. Checking it
            // inside the branch makes the ordering deterministic instead.
            //
            // (Nothing can double-handle it either way: whichever of the two
            // runs first cancels the event, and a cancelled event is not
            // delivered to listeners that did not ask for cancelled ones.)
            if (com.example.cuffedaddon.necklace.NecklaceUtil.removeNecklace(actor, target)) {
                event.setCancellationResult(InteractionResult.CONSUME);
                event.setCanceled(true);
                return;
            }
            boolean handled = tryUnequipHeadCrouch(targetCap, target, actor);
            event.setCancellationResult(handled ? InteractionResult.SUCCESS : InteractionResult.FAIL);
            event.setCanceled(true);
            return;
        }

        // Anything else aimed at a wall-posed target: block it outright -
        // no frisking/anchoring/escort carve-out here (see class doc).
        event.setCancellationResult(InteractionResult.FAIL);
        event.setCanceled(true);
    }

    /** Shared by the key-item head-unequip branch above. */
    private static boolean tryUnequipHeadKeyed(RestrainableCapability targetCap, ServerPlayer target,
                                                ServerPlayer actor, ItemStack keyStack) {
        AbstractRestraint restraint = targetCap.getHeadRestraint();
        if (restraint == null) return false;
        if (restraint.getKeyItem() != null && restraint.getKeyItem() != keyStack.getItem()) return false;
        return targetCap.TryUnequipRestraint(target, actor, RestraintType.Head);
    }

    /** Shared by the crouch+empty-hand head-unequip branch above. */
    private static boolean tryUnequipHeadCrouch(RestrainableCapability targetCap, ServerPlayer target, ServerPlayer actor) {
        AbstractRestraint restraint = targetCap.getHeadRestraint();
        if (restraint == null || restraint.getKeyItem() != null) return false;
        return targetCap.TryUnequipRestraint(target, actor, RestraintType.Head);
    }

    // ------------------------------------------------- client re-sync hooks

    /**
     * Re-sends the wall pose to a client that starts tracking an already-posed
     * player.
     *
     * <p><b>The bug this closes.</b> {@code WallPoseSyncPacket} writes the
     * TRACKED entity's own client-side capability, and everything that makes a
     * wall-restrained player look restrained reads it back from there -
     * {@code HumanoidModelWallPoseMixin} for the pose, the cuff layer, and
     * {@code ClientWallPoseEvents} for the torso yaw lock. Forge does not sync
     * capabilities, so a client that never received the packet has that
     * capability sitting at its defaults and draws the player standing and free.
     * Until 1.5.14 the packet was only ever sent at the moment the pose was
     * applied or released, so anyone who arrived afterwards never got one.
     *
     * <p>This is the exact counterpart of {@code LiePoseEvents#onStartTracking},
     * which the Bed Restraint has had all along - the wall pose was simply
     * missed when it was merged in at 1.4.14.
     */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof net.minecraft.world.entity.LivingEntity tracked)) return;
        IWallPose cap = WallPoseUtil.get(tracked);
        if (cap == null || !cap.isPosed()) return;
        if (!(event.getEntity() instanceof ServerPlayer viewer)) return;

        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer),
                new WallPoseSyncPacket(tracked.getId(), true, cap.getLockedYaw(), cap.getLockedSlot(),
                        cap.getLockedX(), cap.getLockedY(), cap.getLockedZ()));
    }

    /**
     * Re-sends the wall pose to a player who logs back in still restrained.
     *
     * <p>The capability is {@code ICapabilitySerializable}, so the SERVER
     * remembers the pose across a disconnect and {@code onPlayerTick} keeps
     * pinning them - but their own reconnecting client knew nothing about it.
     * [stated] confirmed the symptom from a singleplayer relog: no pose, no
     * cuffs rendered, and "I can still turn my body around", that last one
     * because the torso yaw lock in {@code ClientWallPoseEvents} is keyed off
     * this same client-side capability. One packet fixes all three.
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        IWallPose cap = WallPoseUtil.get(player);
        if (cap == null || !cap.isPosed()) return;

        NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new WallPoseSyncPacket(player.getId(), true, cap.getLockedYaw(), cap.getLockedSlot(),
                        cap.getLockedX(), cap.getLockedY(), cap.getLockedZ()));
    }
}
