package com.example.cuffedaddon.pose;

import com.example.cuffedaddon.CuffedAddon;
import com.example.cuffedaddon.network.LiePoseSyncPacket;
import com.example.cuffedaddon.network.NetworkHandler;
import com.lazrproductions.cuffed.api.CuffedAPI;
import com.lazrproductions.cuffed.cap.RestrainableCapability;
import com.lazrproductions.cuffed.items.base.AbstractRestraintKeyItem;
import com.lazrproductions.cuffed.restraints.RestraintAPI;
import com.lazrproductions.cuffed.restraints.base.AbstractHeadRestraint;
import com.lazrproductions.cuffed.restraints.base.AbstractRestraint;
import com.lazrproductions.cuffed.restraints.base.RestraintType;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Locks a lie-posed player's position/rotation every tick and blocks every
 * form of action - movement, attacking, interacting, using items - the same
 * technique BedRestraintEvents used (re-pin every tick rather than teleport
 * once, only actually teleport when genuinely drifted so the client doesn't
 * feel a constant reconciliation "fix"). See LiePoseUtil for how the pose
 * gets applied/cleared (the /test command in ModCommands, for now).
 *
 * Deliberately never touches Pose (no setPose() calls anywhere in this
 * package) - both visual mixins (HumanoidModelLiePoseMixin,
 * LivingEntityRendererLiePoseMixin) gate entirely on this addon's own
 * ILiePose capability instead. See /areas/cuffedaddon.md for the full
 * explanation of why forcing Pose.SLEEPING every tick was tried before and
 * caused camera/dimension jitter - do not reintroduce it without a very
 * good reason.
 *
 * The collision hitbox is re-asserted every tick too (see
 * LiePoseUtil.buildLieBoundingBox) - a plain Entity#setBoundingBox() call,
 * not a Pose/dimensions mixin, since nothing else would ever trigger a
 * recompute for a player who can no longer move.
 *
 * The selected hotbar slot is locked the same way position is (correct it
 * back every tick, rather than trying to prevent the key press itself,
 * which Forge can't cancel - see ClientboundSetCarriedItemPacket usage
 * below). Opening the inventory screen itself is blocked client-side
 * instead (ClientLiePoseEvents.onScreenOpening), since that's pure client
 * UI with no server-side event to hook.
 */
@Mod.EventBusSubscriber(modid = CuffedAddon.MODID)
public class LiePoseEvents {

    /**
     * ROUND 15 ROOT CAUSE (found by reading Cuffed's own PlayerMixin
     * directly, not guessed - the round 14 off-hand theory was WRONG,
     * [stated] confirmed only ever using the main hand): Cuffed has its
     * OWN, entirely separate "self-restrain when your arm swings" feature
     * (PlayerMixin#tick, gated on the ALLOW_SELF_RESTRAINING config) that
     * runs every server tick for EVERY player and checks vanilla's own
     * "swinging" animation flag - with NO awareness of what caused the
     * swing. Right-clicking an entity to interact with it plays the same
     * arm-swing animation attacking does, so EVERY interaction this addon
     * handles with a posed target ALSO, as a same-tick side effect, sets
     * off Cuffed's own self-restrain check on the ACTOR: it reads the
     * actor's OWN look angle (steeply down when looking at someone lying
     * on a low bed - lands in Cuffed's own LEG bracket) and whatever's
     * STILL in their main hand, then self-applies a restraint built from
     * that item. This explains "only with more than one at a time": with
     * exactly one item, the stack empties out after this addon's own
     * head-restraint application, leaving nothing in hand for this side
     * effect to find; with two or more, the same item is still sitting in
     * hand a moment later when Cuffed's own check runs. Same mechanism
     * for the crouch+empty-hand removal case, off whatever was left in
     * hand at that moment.
     *
     * This is Cuffed's own code, working as intended for its actual
     * purpose (letting a player restrain THEMSELVES by swinging while
     * holding a restraint item) - it's only a problem here because OUR
     * interactions with a posed target happen to also trigger the same
     * swing flag as a side effect. Can't be mixed around without directly
     * conflicting with Cuffed's own mixin on the same vanilla method, so
     * instead this addon snapshots the actor's own arm/leg-restrained
     * state the moment it starts handling an interaction with a posed
     * target (see onInteractWithPosedTarget below), then checks again for
     * a few ticks afterward (consumeSelfRestraintGuard, called from
     * onPlayerTick for every player, not just posed ones) and silently
     * undoes (via Cuffed's own TryUnequipRestraint, which already returns
     * the item to its owner) anything that appeared that wasn't there
     * before - in that narrow window, any NEW restraint on the actor can
     * only have come from this same-tick side effect, not a genuine
     * self-restrain gesture.
     */
    private static final Map<UUID, int[]> PENDING_SELF_RESTRAINT_GUARD = new HashMap<>();
    private static final int SELF_RESTRAINT_GUARD_TICKS = 2;

    /**
     * ROUND 18: [stated] confirmed the round 16 guard DOES work (the
     * spurious restraint gets removed and refunded), but asked whether the
     * actor could avoid seeing it get applied-then-instantly-removed at
     * all, rather than just cleaning it up a moment later. Reflects
     * directly into LivingEntity's own "swinging" field (verified mapping:
     * SRG f_20911_, dev/official "swinging") and force-clears it the
     * instant this addon starts handling an interaction with a posed
     * target - BEFORE Cuffed's own PlayerMixin#tick() (which is what
     * actually reads this flag) runs for that same tick. Interaction
     * packets are processed before entity ticking each server tick, so
     * this addon's own interaction handling (which sets the guard map
     * entry below) always happens first within the tick; suppressing the
     * flag right there, at the START of that same tick's PlayerTickEvent
     * (still before Player#tick()'s own body, i.e. before Cuffed's TAIL
     * injection runs), should stop Cuffed's self-restrain check from ever
     * firing in the first place - no visible flicker, nothing to clean up.
     * The round 16 reactive cleanup (consumeSelfRestraintGuard, at
     * Phase.END) is kept regardless, as a safety net for the case this
     * doesn't catch (e.g. the actor was already mid-swing, for an
     * unrelated reason, at the exact moment this interaction happened -
     * Cuffed's own "hasProcessedSwing" only fires once per continuous
     * swing streak, so it may have already fired on an earlier tick this
     * addon never saw).
     */
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
        if (SWINGING_FIELD == null) return; // reflection failed at class-init - safety net still covers this

        int[] guard = PENDING_SELF_RESTRAINT_GUARD.get(player.getUUID());
        // Only on the exact tick the guard was just set (full tick count
        // still present, nothing decremented yet) - don't keep suppressing
        // swings for the whole guard window, or a genuinely unrelated swing
        // the actor does a tick or two later would get silently eaten too.
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
        Player player = event.player;
        Level level = player.level();
        if (level.isClientSide) return;

        // Checked for EVERY player, not just posed ones - this is about
        // the ACTOR (see this class's own doc above), who is never posed.
        if (player instanceof ServerPlayer serverPlayer) {
            if (event.phase == TickEvent.Phase.START) {
                suppressSpuriousSwing(serverPlayer);
            } else if (event.phase == TickEvent.Phase.END) {
                consumeSelfRestraintGuard(serverPlayer);
            }
        }

        if (event.phase != TickEvent.Phase.END) return;

        ILiePose cap = LiePoseUtil.get(player);
        if (cap == null || !cap.isPosed()) return;

        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        player.setShiftKeyDown(false);
        player.setSprinting(false);

        // Body orientation locked; look direction (mouse look) stays free -
        // the render mixin reads cap.getLockedYaw() directly rather than
        // trusting this field's interpolated state at render time, so this
        // is really just for anything else (non-rendering) that reads it.
        player.setYBodyRot(cap.getLockedYaw());

        // Only teleport if genuinely drifted - see BedRestraintEvents' own
        // comment on this same pattern for why unconditional every-tick
        // teleports feel like the camera "constantly fixing itself". This
        // corrects the SERVER's own authoritative position/the target's
        // own client (connection.teleport only ever reaches them, never
        // observers - see ClientLiePoseEvents' own doc for the fix that
        // actually matters for observers: an unconditional client-side
        // position pin every tick, which makes what happens here a
        // pure formality in the normal case).
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

        // Backstop for externally-opened containers (chests, etc - already
        // also blocked at the RightClickBlock level below). NOTE: this does
        // NOT block clicking around inside the player's own inventory screen
        // - see ClientLiePoseEvents' onScreenOpening for that.
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }

        // Re-assert the shrunk collision box every tick, pinned to the
        // locked position/yaw - see LiePoseUtil.setPosed's own doc for why
        // a one-time set at apply-time isn't enough on its own (nothing else
        // triggers a recompute for a player who can no longer move), and
        // buildLieBoundingBox's own doc for why yaw is needed at all (the
        // box has to reach toward where the head ends up, not just sit
        // centered on the feet/pivot).
        player.setBoundingBox(LiePoseUtil.buildLieBoundingBox(
                cap.getLockedX(), cap.getLockedY(), cap.getLockedZ(), cap.getLockedYaw()));

        // Locks the selected hotbar slot too - InputEvent.Key (the 1-9 keys
        // themselves) isn't cancellable in Forge 1.20.1, so this is a
        // correction-based lock instead of a prevention-based one, same
        // shape as the position lock above: let the client-side selection
        // change happen, then force it back and tell the client's HUD to
        // match. player.getInventory().selected is a plain int field, not
        // something with its own setter - direct field write.
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.getInventory().selected != cap.getLockedSlot()) {
            serverPlayer.getInventory().selected = cap.getLockedSlot();
            serverPlayer.connection.send(new ClientboundSetCarriedItemPacket(cap.getLockedSlot()));
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (LiePoseUtil.isPosed(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() != null && LiePoseUtil.isPosed(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (LiePoseUtil.isPosed(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (LiePoseUtil.isPosed(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /**
     * ROUND 19: [stated] reported still being able to sleep in, and set a
     * respawn point via, a bed with a lie-posed player currently on it -
     * this addon's own `onRightClickBlock` above only blocks a POSED
     * player's own clicks (they can't act at all), it never considered the
     * BED itself being unsafe to use while someone else is restrained on
     * it. Cuffed's own `BunkBlock`/vanilla `BedBlock` have no awareness of
     * this addon's capability at all, so their normal sleep/set-spawn
     * `use()` was always going through untouched for anyone else.
     *
     * ROUND 20: round 19's fix used a 2-block RADIUS check, which
     * [stated] correctly flagged as wrong - a second, unrelated bed placed
     * right next to an occupied one would get swept up and blocked too.
     * Fixed to identify the EXACT bed instead: `LiePoseUtil.setPosedOnBed`
     * now records the bed's own FOOT block position on the capability
     * (`ILiePose#getLockedBedPos`) at apply time, and this listener
     * normalizes the CLICKED block down to its own foot position the same
     * way `BedRestraintItem` does (via `BedBlock.PART`/`FACING`) before
     * comparing - an exact BlockPos match against a posed player's own
     * recorded bed, not a radius guess. A neighboring, unoccupied bed one
     * block over now stays fully usable.
     */
    @SubscribeEvent
    public static void onRightClickBed(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        net.minecraft.core.BlockPos pos = event.getPos();
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        if (!state.getBlock().isBed(state, level, pos, event.getEntity())) return;

        net.minecraft.core.Direction facing = state.getValue(net.minecraft.world.level.block.BedBlock.FACING);
        net.minecraft.world.level.block.state.properties.BedPart part =
                state.getValue(net.minecraft.world.level.block.BedBlock.PART);
        // FACING points away from the foot, toward the head - same
        // normalization BedRestraintItem uses when first placing the pose.
        net.minecraft.core.BlockPos clickedFootPos = part == net.minecraft.world.level.block.state.properties.BedPart.FOOT
                ? pos
                : pos.relative(facing.getOpposite());

        for (Player p : level.players()) {
            ILiePose posedCap = LiePoseUtil.get(p);
            if (posedCap == null || !posedCap.isPosed()) continue;

            net.minecraft.core.BlockPos occupiedBedPos = posedCap.getLockedBedPos();
            if (clickedFootPos.equals(occupiedBedPos)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (LiePoseUtil.isPosed(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (LiePoseUtil.isPosed(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent event) {
        if (event.getEntity() instanceof Player player && LiePoseUtil.isPosed(player)) {
            event.setCanceled(true);
        }
    }

    /**
     * Blocks dropping items (Q / Ctrl+Q) while posed. Player#drop(boolean)
     * already REMOVES the item from the inventory before this event fires
     * (vanilla: Inventory#removeFromSelected happens first, then the
     * ItemEntity is created and this event posted) - cancelling alone would
     * destroy the item, so it's added back to the inventory first.
     */
    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        Player player = event.getPlayer();
        if (!LiePoseUtil.isPosed(player)) return;

        player.getInventory().add(event.getEntity().getItem());
        event.setCanceled(true);
    }

    /**
     * Handles EVERY interaction aimed at a lie-posed TARGET (someone else
     * right-clicking them, not the posed player's own - already blocked
     * unconditionally above) directly, ourselves - head restraints can be
     * applied/removed, arm/leg restraints are blocked outright, and the
     * escort feature is disabled entirely for a posed target.
     *
     * ROUND 8 REDESIGN: this used to forward everything into Cuffed's own
     * RestrainableCapability#onInteractedByOther with a manually-forced
     * height (always > 1.5, its own "head" threshold), reasoning that since
     * that method decides head/arm/leg purely from a height value, forcing
     * it should make every case resolve as head. That reasoning holds up
     * under static reading of Cuffed's source, and worked for the reported
     * "no matter where I aim it goes to the legs" apply-side bug - but round
     * 8 found the SAME approach didn't work for the empty-hand-plus-
     * crouching REMOVAL case: [stated] confirmed removing a keyless head
     * restraint (Duck Tape Head / this addon's own Rope Head, both
     * getKeyItem()==null) this way still ends up touching the legs. That
     * couldn't be explained by re-reading onInteractedByOther's own code
     * (with height forced to > 1.5, its internal branching should be
     * structurally incapable of ever reaching the leg bracket) - something
     * about how Cuffed dispatches to that method for THIS specific
     * interaction shape isn't fully understood, despite real effort tracing
     * it through the actual source.
     *
     * Rather than keep guessing at a mechanism inside Cuffed's own code that
     * isn't behaving as its source suggests it should, this bypasses
     * onInteractedByOther's dispatch (and its height/crouch-based branching)
     * ENTIRELY for the cases that matter here, and calls the underlying
     * equip/unequip operations directly - RestrainableCapability#
     * TryEquipRestraint/TryUnequipRestraint/getHeadRestraint are public
     * methods on the concrete class (not on the IRestrainableCapability
     * interface, but real, callable via a cast - the same pattern Cuffed's
     * OWN code uses internally, e.g. AbstractRestraintItem.dispenseRestraint).
     * This removes the entire class of bug regardless of its true root
     * cause, since nothing here depends on Cuffed's own height computation
     * or crouch-branch dispatch at all anymore.
     *
     * HIGHEST priority is required to win the race against Cuffed's own
     * listener (EventPriority.HIGH on the same event) - same pattern
     * already used for the old Bed Restraint's Handcuffs Key unlock race,
     * see /areas/cuffedaddon.md. Every branch below ends by cancelling the
     * event, so Cuffed's own listener never gets a chance to run its own
     * (still height-based, still not fully trusted for this pose) logic
     * afterward - except the final fallback (frisking, anchoring, and
     * anything else not covered above), which is deliberately left alone.
     *
     * ROUND 14: added an off-hand/main-hand guard here, theorizing a
     * client-side prediction gap was letting a second item get processed
     * via the off hand on the same click. **ROUND 15: [stated] confirmed
     * this did NOT fix the bug, and confirmed only the main hand was ever
     * used (no off-hand item at all)** - the round 14 theory was wrong.
     * The MAIN_HAND-only guard below is left in place (still reasonable
     * defensively) but was NOT the real fix. The actual root cause, found
     * in round 15 by reading Cuffed's own PlayerMixin directly, is
     * documented on this class's own PENDING_SELF_RESTRAINT_GUARD field
     * above - Cuffed's own self-restrain-on-swing feature reacting to the
     * ordinary arm-swing animation that right-clicking an entity plays,
     * same as attacking does. The snapshot-and-clean-up-after fix for
     * that lives in onPlayerTick/consumeSelfRestraintGuard; this method
     * only needs to take the snapshot at the right moment (see below).
     * ROUND 16: [stated] compared this to Cuffed's own pillory (which
     * allows all three restraint types and never touches the target's
     * hitbox) and asked whether this addon was over-restricting arm/leg.
     * Agreed for the restraint TYPES at the time, and widened arm/leg to
     * the same direct-equip pattern head already used. See LiePoseUtil's
     * own doc for why the HITBOX point never carried over the same way
     * (pillory never rotates the model, so it never needs to).
     * **ROUND 19: reverted the arm/leg widening** - real testing showed a
     * visual conflict the pillory comparison didn't predict (Cuffed's own
     * restraint render layer draws a second, real set of cuffs directly on
     * top of this addon's own always-on cosmetic ones - see the arm/leg
     * block below for the full explanation). Head restraints are the only
     * type applicable to a posed target again, same as before round 16.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteractWithPosedTarget(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof ServerPlayer target)) return;
        if (!LiePoseUtil.isPosed(target)) return;
        if (!(event.getEntity() instanceof ServerPlayer actor)) return;
        if (LiePoseUtil.isPosed(actor)) return; // a posed actor can't interact with anything at all - see onEntityInteract

        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
            // See this method's class doc (round 14) - only ever act on the
            // main hand; flatly deny anything else aimed at a posed target
            // rather than letting a second (off-hand) item get processed on
            // the same click.
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
            return;
        }

        // Round 15: snapshot the actor's OWN restraint state right before
        // letting this interaction (and the arm-swing animation that comes
        // with it) happen - see PENDING_SELF_RESTRAINT_GUARD's doc above.
        // Taken here regardless of what this interaction turns out to be,
        // since the swing (and therefore Cuffed's own side effect) happens
        // no matter which branch below ends up running.
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

        // Handcuffs Key removes the LIE POSE itself (not a head restraint -
        // this is specifically about undoing the Bed Restraint item) and
        // gives the actor a Bed Restraint item back, mirroring how Cuffed's
        // own restraints return their item on unequip. Checked before the
        // generic AbstractRestraintKeyItem branch below (Handcuffs Key
        // likely also satisfies that interface) so this takes priority for
        // this specific item regardless of whatever head restraint (if
        // any) the target currently has.
        if (stack.is(com.lazrproductions.cuffed.init.ModItems.HANDCUFFS_KEY.get())) {
            LiePoseUtil.setPosed(target, false);

            ItemStack bedRestraint = new ItemStack(com.example.cuffedaddon.init.ModItems.BED_RESTRAINT.get());
            if (!actor.getInventory().add(bedRestraint)) {
                actor.drop(bedRestraint, false);
            }

            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }

        // ROUND 21: [stated] asked for the Possessions Box (Cuffed's own
        // frisking item) to work on a posed target too. Cuffed's own
        // listener already handles it (see ModServerEvents -
        // PossessionsBox.frisk gets called there), but only when
        // targetCap.armsRestrained() is true - our posed target is fully
        // incapacitated via this addon's OWN pose lock, not via Cuffed's
        // arm-restraint capability (kept deliberately unused per the round
        // 19 revert above), so that condition is never true here and
        // Cuffed's own check would silently do nothing. Handled directly
        // instead, the same public API Cuffed's own listener itself calls -
        // no restraint-type or height dispatch involved at all here, so
        // this doesn't run into the same class of problem the equip/
        // unequip branches above do.
        //
        // ROUND (this session): [stated] reported the Curios-aware column
        // (PossessionsBoxCurioEvents) never showing up for a bed-restrained
        // target, even with Curios installed and items actually worn.
        // Root cause: PossessionsBoxCurioEvents.onFriskAttempt has its OWN
        // separate EntityInteract listener (also HIGHEST priority) that
        // only opens the Curios-aware CurioFriskingMenu when
        // targetCap.armsRestrained() is true - same condition, same gap,
        // as the plain-frisking one this branch already works around. That
        // listener never fires for our own posed targets, so this branch
        // was always falling through to the plain (non-Curios) frisk call
        // below unconditionally, regardless of whether Curios was even
        // installed. Fixed by mirroring PossessionsBoxCurioEvents' own
        // isLoaded("curios") guard right here and dispatching to
        // CurioFriskCompat.frisk instead when it's present - same
        // class-isolation pattern (only ever touch Curios types from
        // inside that guard) documented on CurioFriskCompat itself.
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
                // An "empty" Bundle is NOT an empty ItemStack (still a real
                // Bundle you're holding) and isn't in Cuffed's per-item
                // restraint registry either (special-cased elsewhere in
                // Cuffed's own code) - matches AbstractRestraintItem.
                // dispenseRestraint's own condition for it.
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

        // ROUND 19 REVERT: round 17 changed this to allow arm/leg restraints
        // on a posed target, reasoning from Cuffed's own pillory (which
        // allows all three types). [stated] confirmed this was wrong for
        // THIS feature specifically: Cuffed's real restraint render layer
        // (RestraintEntityLayer) copies this addon's own spread-pose data
        // into its restraint model (via HumanoidModel#copyPropertiesTo) and
        // draws a SECOND, real set of cuffs directly on top of - not
        // instead of - this addon's own always-shown cosmetic
        // LiePoseHandcuffsLayer, since that layer is intentionally NOT
        // gated on an actual Cuffed restraint being equipped (see its own
        // doc). Pillory doesn't have this conflict because it has no
        // equivalent always-on cosmetic restraint layer of its own - Lie
        // Pose does, and the whole point of it is that the pose itself
        // (limbs + cosmetic cuffs) already fully represents "arms/legs
        // restrained" without needing a real Cuffed arm/leg restraint item
        // at all. So: back to blocking arm/leg restraints outright, same
        // as before round 17 - the pillory comparison held for the
        // interaction-permission logic (see this method's class doc) but
        // not for this specific visual conflict.
        if (RestraintAPI.IsArmRestraintItem(stack) || RestraintAPI.IsLegRestraintItem(stack)) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
            return;
        }

        if (stack.getItem() instanceof AbstractRestraintKeyItem) {
            // Round 19: back to head-only, matching the arm/leg revert
            // above - arm/leg can no longer be equipped on a posed target,
            // so there's nothing for the Arm/Leg tries to ever find.
            boolean handled = tryUnequipKeyed(targetCap, target, actor, RestraintType.Head, stack);
            event.setCancellationResult(handled ? InteractionResult.SUCCESS : InteractionResult.FAIL);
            event.setCanceled(true);
            return;
        }

        if (stack.is(com.lazrproductions.cuffed.init.ModItems.LOCKPICK.get())) {
            // Lockpicking wasn't reported broken - still routed through
            // Cuffed's own dispatch with a forced (head) height, lower-risk
            // than reimplementing progress-tracking/packets ourselves.
            // NOT extended to arm/leg in round 16 along with the rest of
            // this method - Cuffed's lockpick flow is a stateful,
            // packet-driven minigame (sendLockpickBeginPickingRestraintPacket)
            // rather than a single equip/unequip call, so it needs its own
            // dedicated look before being widened the same way; left as-is
            // for now.
            boolean handled = targetCap.onInteractedByOther(target, actor, 2.0d, stack, event.getHand(), false);
            event.setCancellationResult(handled ? InteractionResult.SUCCESS : InteractionResult.FAIL);
            event.setCanceled(true);
            return;
        }

        if (stack.isEmpty() && actor.isCrouching()) {
            // Round 19: back to head-only, matching the arm/leg revert
            // above.
            boolean handled = tryUnequipCrouch(targetCap, target, actor, RestraintType.Head);
            event.setCancellationResult(handled ? InteractionResult.SUCCESS : InteractionResult.FAIL);
            event.setCanceled(true);
            return;
        }

        if (stack.isEmpty() && !actor.isCrouching()) {
            // This is what Cuffed's own escort feature would otherwise
            // trigger on (empty hand, not crouching, target restrained) -
            // disabled unconditionally for a posed target regardless of
            // what restraints (if any) they have, per explicit request.
            event.setCanceled(true);
            return;
        }

        // Anything else (frisking, anchoring, etc) - left alone for Cuffed's own listener.
    }

    /** Shared by the key-item unequip branch above. */
    private static boolean tryUnequipKeyed(RestrainableCapability targetCap, ServerPlayer target, ServerPlayer actor,
                                            RestraintType type, ItemStack keyStack) {
        AbstractRestraint restraint = targetCap.getHeadRestraint();
        if (restraint == null) return false;
        if (restraint.getKeyItem() != null && restraint.getKeyItem() != keyStack.getItem()) return false;
        return targetCap.TryUnequipRestraint(target, actor, type);
    }

    /** Shared by the crouch+empty-hand unequip branch above. */
    private static boolean tryUnequipCrouch(RestrainableCapability targetCap, ServerPlayer target, ServerPlayer actor,
                                             RestraintType type) {
        AbstractRestraint restraint = targetCap.getHeadRestraint();
        if (restraint == null || restraint.getKeyItem() != null) return false;
        return targetCap.TryUnequipRestraint(target, actor, type);
    }

    /**
     * Fixes the capability-sync gap: the sync packet only fires when the pose
     * is applied/cleared, so a client that starts tracking an already-posed
     * player later (or the posed player relogging) would otherwise never
     * learn about it and silently render them standing.
     */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof Player tracked)) return;
        ILiePose cap = LiePoseUtil.get(tracked);
        if (cap == null || !cap.isPosed()) return;
        if (!(event.getEntity() instanceof ServerPlayer viewer)) return;

        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer),
                new LiePoseSyncPacket(tracked.getId(), true, cap.getLockedYaw(), cap.getLockedSlot(),
                        cap.getLockedX(), cap.getLockedY(), cap.getLockedZ()));
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ILiePose cap = LiePoseUtil.get(player);
        if (cap == null || !cap.isPosed()) return;

        NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new LiePoseSyncPacket(player.getId(), true, cap.getLockedYaw(), cap.getLockedSlot(),
                        cap.getLockedX(), cap.getLockedY(), cap.getLockedZ()));
    }
}
