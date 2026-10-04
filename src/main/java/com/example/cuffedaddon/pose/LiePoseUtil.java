package com.example.cuffedaddon.pose;

import com.example.cuffedaddon.capability.ModCapabilities;
import com.example.cuffedaddon.network.LiePoseSyncPacket;
import com.example.cuffedaddon.network.NetworkHandler;
import com.lazrproductions.cuffed.api.CuffedAPI;
import com.lazrproductions.cuffed.cap.base.IRestrainableCapability;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;

public class LiePoseUtil {

    // How far the box extends from the locked (feet) position toward the
    // head, and how wide/tall it is perpendicular to that. See
    // buildLieBoundingBox's own doc for why this needs a direction at all.
    private static final double BODY_LENGTH = 1.6;
    private static final double HALF_WIDTH = 0.4;

    // ROUND (this session): [stated] confirmed via F3+B screenshot the box
    // is visibly oversized - "longer than 2 blocks" and "twice as tall as
    // it needs to be". HEIGHT was 0.6, meant to be the body's actual
    // front-to-back THICKNESS once flipped flat (a standing player's
    // WIDTH becomes horizontal, but their much thinner front-to-back
    // depth becomes vertical) - 0.6 was really closer to the original
    // standing WIDTH, roughly double a lying body's real thickness.
    // Halved to 0.3 as a reasoned estimate, not a re-measured value -
    // flag if it still looks off either way once tested.
    private static final double HEIGHT = 0.3;

    /**
     * NO LONGER FEEDS THE RENDER (1.5.11) - it now only feeds
     * {@link #bodyExtendDirection}, i.e. the hitbox and the camera.
     *
     * <p>It used to be added into the render rotation as
     * {@code lockedYaw + BODY_YAW_OFFSET + RENDER_MODEL_EXTRA_YAW_DEGREES}, which was
     * the wrong SHAPE of correction: the render yaw is a mirror of lockedYaw, not an
     * offset from it, so no constant added here could ever make the two agree at more
     * than half the compass. See {@link #renderYawFor} for the derivation and for what
     * replaced it. The hitbox/camera side was always correct and is untouched, which is
     * exactly why this constant survives.
     *
     * <p>Round 4's F3+B hitbox test confirmed this value for the hitbox, and that still
     * stands. Do not change it without re-confirming the body direction in game.
     */
    public static final float BODY_YAW_OFFSET = 90.0F;

    /**
     * bodyExtendDirection's OWN extra offset, on top of BODY_YAW_OFFSET -
     * exists because the render mixin's compound rotation (Axis.YP(bodyYaw)
     * -> Axis.ZP(90) -> Axis.YP(270)) has a fixed +90-degree baseline built
     * into it, independent of BODY_YAW_OFFSET, that a plain
     * Vec3.directionFromRotation(0, yaw) call has no equivalent for.
     *
     * This was round 4's actual bug: at BODY_YAW_OFFSET=0 (round 2),
     * bodyExtendDirection pointed at the player's original facing (camera
     * went "into the tree") while the render mixin ALREADY pointed 90
     * degrees off that (body rendered to the right) - even at the SAME
     * input offset, they disagreed by a fixed 90 degrees. Adding
     * BODY_YAW_OFFSET (90) to both in round 3 moved them in lockstep
     * (confirmed - both shifted by the same amount) but never closed that
     * original 90-degree gap, since it was never really being applied to
     * bodyExtendDirection in the first place: round 4 confirmed the render
     * mixin now correctly points straight back, while bodyExtendDirection
     * (missing this baseline) still only reached "90 degrees right of
     * back" i.e. exactly where the render mixin USED to point before it
     * was fixed. This constant is that missing baseline, making
     * bodyExtendDirection match the render mixin's ACTUAL output instead of
     * a plain, baseline-less direction rotation.
     *
     * Only needs to change if the render mixin's other two hardcoded angles
     * (the 90.0F Axis.ZP rotation, or the 270.0F second Axis.YP rotation)
     * are ever changed - it is not a "reasoned guess" like BODY_YAW_OFFSET
     * was; it's derived directly from round 2 vs round 4's two confirmed
     * data points (render output = bodyExtendDirection's old output + 90,
     * consistently, at two different offset values), so should not need
     * further guessing.
     */
    private static final float RENDER_COMPOUND_BASELINE = 90.0F;

    // RETIRED AT 1.5.11 - deliberately left here as a signpost rather than deleted.
    // This was the flat +180 added when the "model renders 180 degrees opposite the
    // bed" bug was first reported. It was treating a MIRRORED yaw as if it were an
    // OFFSET one, so it fixed NORTH/SOUTH beds and left EAST/WEST beds wrong for
    // several versions. See renderYawFor below for the real fix and its derivation.
    // Do not reintroduce it: adding any constant to lockedYaw cannot fix a sign.

    /**
     * Applies the lie-flat render rotation to poseStack if this entity is a
     * posed player, and reports whether it did (so the calling mixin knows
     * whether to cancel the rest of its own target method). Pure render-time
     * transform, no state changes.
     */
    public static boolean applyRenderRotationIfPosed(net.minecraft.world.entity.LivingEntity entity,
                                                       com.mojang.blaze3d.vertex.PoseStack poseStack) {
        // LivingEntity, not Player - 1.5.11. A bed-restrained Fake Players fake
        // player is posed by this exact function; see
        // fakeplayer/FakeStationaryUtil for why the whole lie pose is reused
        // rather than rebuilt for that entity. Every existing caller already
        // passed a LivingEntity (the mixin's own parameter type), so nothing
        // narrowed here.
        if (!isPosed(entity)) {
            return false;
        }
        ILiePose cap = get(entity);
        if (cap == null) {
            return false;
        }
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(renderYawFor(cap.getLockedYaw())));
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(90.0F));
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(270.0F));
        return true;
    }

    /**
     * The first of the three rotations, and the one that was WRONG until 1.5.11.
     *
     * <h2>The bug: the yaw was mirrored, not offset</h2>
     * This used to be {@code lockedYaw + BODY_YAW_OFFSET + RENDER_MODEL_EXTRA_YAW_DEGREES}
     * - i.e. {@code lockedYaw + 270}. The correct value is {@code 270 - lockedYaw}. Those
     * two agree exactly when {@code lockedYaw} is 0 or 180 (a bed running NORTH/SOUTH) and
     * are exactly 180 degrees apart when it is 90 or 270 (a bed running EAST/WEST). That is
     * the whole of [stated]'s report that a bed restraint looked right on one bed and
     * backwards on another: <b>it was never the block type.</b> A Cuffed Bunk places
     * SIDEWAYS relative to the placer ({@code getHorizontalDirection().getCounterClockWise}),
     * while a vanilla bed places ALONG the look direction, so placing one of each from the
     * same spot lands them 90 degrees apart - which is exactly how an axis-dependent bug
     * comes to look like a block-type-dependent one.
     *
     * <h2>Why it survived since 1.3.x</h2>
     * {@code RENDER_MODEL_EXTRA_YAW_DEGREES} (the flat +180 added when the "model renders
     * 180 degrees opposite the bed" bug was first reported) corrected the NORTH/SOUTH case
     * and left EAST/WEST wrong - it swapped which half of the compass was broken instead of
     * fixing the sign. Every round of testing after that happened to use a bed on the other
     * axis. <b>This affects real players exactly as much as fake ones</b>; it is not a Fake
     * Players bug at all, it was only found because 1.5.11 put the same pose on a second
     * kind of entity.
     *
     * <h2>How the correct value was derived, so it needn't be guessed at again</h2>
     * The compound rotation is {@code YP(a) -> ZP(90) -> YP(270)}, which maps the model's
     * own feet-to-head axis to the world direction {@code (-cos a, 0, sin a)}. Setting that
     * equal to the bed's FACING step vector and solving gives {@code a = 90 - facing.toYRot()},
     * and since {@code lockedYaw = facing.getOpposite().toYRot()}, that is
     * {@code a = 270 - lockedYaw}. Two independent confirmations:
     * <ul>
     *   <li>it reproduces vanilla's own {@code LivingEntityRenderer.sleepDirectionToRotation}
     *       for all four bed directions (SOUTH 90, WEST 0, NORTH 270, EAST 180) - the same
     *       three-rotation structure vanilla uses to lay a sleeping player on a bed;</li>
     *   <li>it agrees with {@link #bodyExtendDirection} - the hitbox and camera direction,
     *       which was always correct - at EVERY yaw, not just the cardinals. The old formula
     *       disagreed with it at 22 of 24 sampled yaws, which is what a mirror looks like.</li>
     * </ul>
     *
     * <p><b>NORTH/SOUTH beds come out byte-identical to the old formula</b>, so nothing that
     * was already confirmed working can regress.
     */
    public static float renderYawFor(float lockedYaw) {
        return RENDER_YAW_BASE - lockedYaw;
    }

    /**
     * The 270 in {@code 270 - lockedYaw}. Kept as a named constant purely so the sign of the
     * lockedYaw term is impossible to miss - that sign IS the bug this replaced.
     */
    private static final float RENDER_YAW_BASE = 270.0F;

    /**
     * Round 14: user confirmed the body still sat slightly too far toward
     * the pillow/head end even after round 13's centering fix - an
     * additional nudge toward the foot edge, on top of that existing
     * centering math, applied along the same bodyExtendDirection axis (see
     * setPosedOnBed's centerShift). Best-effort magnitude, not derived -
     * retune (larger = further toward the foot) if still off after this.
     */
    private static final double FOOT_SIDE_NUDGE = 0.15;

    /**
     * Round 14: user reported the model visibly clipping down into the bed.
     * The locked Y was just the player's own standing-on-the-bed Y, which
     * puts the render/hitbox/camera pivot (all three read this same y -
     * see buildLieBoundingBox and CameraLiePoseMixin) right at the bed's
     * surface height. Once the model flips flat, the model's own WIDTH
     * (used as vertical thickness while lying down) straddles that pivot -
     * roughly half of it extends below, straight into the bed mesh. A small
     * vertical lift moves the pivot clear of the surface. Only applied in
     * setPosedOnBed (not plain setPosed), since this is specifically a
     * bed-surface problem, not a general lying-on-the-ground one.
     */
    public static final double BED_VERTICAL_LIFT = 0.15;

    /**
     * ROUND 18: [stated] confirmed a visible up/down "bobbing" while posed,
     * seen on the RESTRAINED player's own client too (not just bystanders)
     * - meaning the entity's actual position was moving, not just a
     * rendering artifact. Root cause: BED_VERTICAL_LIFT (round 14) leaves
     * the locked Y sitting 0.15 blocks above the bed's real physical
     * surface - a small gap with nothing solid under it. Every tick,
     * ordinary gravity gets applied to the player as part of Player#tick()
     * BEFORE LiePoseEvents' own tick handler runs (Forge's PlayerTickEvent
     * fires at Phase.END, i.e. after that tick's physics already moved
     * them) - so the player actually drops a small amount into that gap
     * each tick, gets detected as "drifted", and gets teleported back up -
     * repeating every tick as a visible sawtooth. Fixed by disabling
     * gravity outright while posed (applyPosed/setPosed(false) below) -
     * the player's position is already fully hand-controlled by the
     * tick-lock regardless, so there's no reason gravity should be acting
     * on them at all in the meantime.
     */
    @Nullable
    public static ILiePose get(net.minecraft.world.entity.LivingEntity entity) {
        return entity.getCapability(ModCapabilities.LIE_POSE).orElse(null);
    }

    /**
     * <b>Cheap gate first, and it matters.</b> Widening this from {@code Player} to
     * {@code LivingEntity} in 1.5.11 also widened WHO asks: the render mixins, the
     * cosmetic cuffs layer and {@code Entity#isPushable} now reach this for every
     * humanoid entity on screen, several times per entity per frame, where the old
     * {@code instanceof Player} used to stop them dead. {@code getCapability} is not
     * free - it walks the entity's provider list - so the class check is done first
     * and only something that could actually be posed reaches the lookup. A skeleton
     * or a villager costs one {@code instanceof} exactly as it did before.
     *
     * <p>The Fake Players half of that test is itself a cached boolean read when that
     * mod is absent (see {@code FakePlayerSupport#isModLoaded}), so this costs nothing
     * extra for anyone not running it.
     */
    public static boolean isPosed(net.minecraft.world.entity.LivingEntity entity) {
        if (!(entity instanceof Player)
                && !com.example.cuffedaddon.fakeplayer.FakePlayerSupport.isFakePlayer(entity)) {
            return false;
        }
        ILiePose cap = get(entity);
        return cap != null && cap.isPosed();
    }

    /**
     * The direction the body actually extends toward (feet -> head) once
     * lying flat, for a given locked yaw - used for BOTH the collision box
     * (buildLieBoundingBox) and the camera offset (CameraLiePoseMixin), so
     * the two are structurally guaranteed to agree with each other. Folds
     * in BOTH constants above (RENDER_COMPOUND_BASELINE + BODY_YAW_OFFSET)
     * so this now matches the render mixin's actual confirmed output
     * exactly, not just moves in step with it - see RENDER_COMPOUND_BASELINE's
     * own doc for why one offset alone wasn't enough (round 4's finding).
     */
    public static Vec3 bodyExtendDirection(float lockedYaw) {
        return Vec3.directionFromRotation(0.0F, lockedYaw + RENDER_COMPOUND_BASELINE + BODY_YAW_OFFSET);
    }

    /**
     * Builds the collision box used while lie-posed, running from the
     * locked (x,y,z) toward the head and padded on all sides.
     *
     * The locked position is always the player's FEET - that's what entity
     * position is, and it's also the pivot LivingEntityRendererLiePoseMixin
     * rotates the whole model around to lie it flat. A box merely CENTERED
     * on that pivot only covers the leg end of the body (this was round 1's
     * bug: "hitbox only as big as the player's legs"). Fixed by computing
     * where the head end actually lands via bodyExtendDirection (the same
     * direction the render mixin actually renders the body toward) and
     * building an axis-aligned box that spans the full pivot-to-head
     * segment, not just a small area around the pivot alone.
     *
     * ROUND 16: [stated] pointed out Cuffed's own pillory doesn't touch the
     * target's hitbox at all, and asked whether this custom box is
     * over-engineered by comparison. The difference is that pillory never
     * changes how the player is RENDERED - they stay in their normal
     * upright pose, just position/rotation-locked next to the block - so
     * their existing normal (upright) hitbox still lines up with what's
     * actually drawn on screen. This addon deliberately renders the body
     * rotated 90 degrees to lie flat (the entire point of the feature), so
     * the normal upright hitbox would no longer line up with the visual at
     * all - it would stand up, invisible, above wherever the body is
     * actually drawn on the bed, matching neither where bystanders would
     * expect to click to interact nor where the body visually is. This
     * custom box is what keeps the collision/interaction target lined up
     * with the (rotated) visual - dropping it would fix nothing that's
     * actually been reported broken and would likely make interacting
     * with a lie-posed player noticeably worse, so it stays. (The arm/leg
     * restraint-type restriction [stated] compared it to was a separate,
     * genuinely over-restrictive piece - see LiePoseEvents' own round 16
     * notes for that fix.)
     */
    /**
     * ROUND (this session): [stated]'s screenshot showed the box longer
     * than it should be - this was the real cause. HALF_WIDTH padding was
     * being added to BOTH X and Z unconditionally, regardless of which
     * axis is actually the body's LENGTH (head-to-foot) vs its WIDTH
     * (perpendicular) - for a body lying along one cardinal axis, that
     * stacked an extra HALF_WIDTH onto BOTH ends of the length too (a
     * dimension that should only span exactly head-to-foot), inflating the
     * total length from BODY_LENGTH (1.6) to BODY_LENGTH + 2*HALF_WIDTH
     * (2.4) - matching "clearly longer than 2 blocks" exactly.
     *
     * Fixed by only applying the width padding to whichever axis is
     * actually PERPENDICULAR to the body's forward direction, scaled by
     * how aligned forward is to each axis (|forward.z| for the X padding,
     * |forward.x| for the Z padding) - for the normal cardinal-aligned
     * case this correctly zeroes out the padding on the length axis
     * entirely and applies it in full on the width axis, and degrades
     * sensibly for any non-cardinal angle too.
     */
    public static AABB buildLieBoundingBox(double x, double y, double z, float yaw) {
        Vec3 forward = bodyExtendDirection(yaw);
        double headX = x + forward.x * BODY_LENGTH;
        double headZ = z + forward.z * BODY_LENGTH;

        double padX = HALF_WIDTH * Math.abs(forward.z);
        double padZ = HALF_WIDTH * Math.abs(forward.x);

        double minX = Math.min(x, headX) - padX;
        double maxX = Math.max(x, headX) + padX;
        double minZ = Math.min(z, headZ) - padZ;
        double maxZ = Math.max(z, headZ) + padZ;

        return new AABB(minX, y, minZ, maxX, y + HEIGHT, maxZ);
    }

    /**
     * Puts the player's ACTUAL collision box back to whatever it would
     * normally be at their current position/pose - plain public API, no
     * mixin/reflection involved (Entity#getDimensions(Pose)/#setBoundingBox
     * are both public). Safe to call even if the box was never touched.
     */
    public static void restoreNormalBoundingBox(net.minecraft.world.entity.LivingEntity entity) {
        entity.setBoundingBox(entity.getDimensions(entity.getPose()).makeBoundingBox(entity.position()));
    }

    /**
     * Applies or clears the pose server-side: on apply, captures the
     * player's CURRENT position, yaw, and selected hotbar slot as the
     * locked pose (no offset math for the position itself, no anchor block
     * - see ILiePose's class doc for why that's deliberate this time). Used
     * by the /test command; also the entry point setPosedOnBed (below)
     * delegates to for the actual capability/precondition/bbox/sync work,
     * via the shared applyPosed helper.
     *
     * Refuses to apply (returns false, does nothing) if the player already
     * has an arm or leg restraint equipped (Cuffed's own
     * IRestrainableCapability#armsRestrained()/legsRestrained()) - this
     * pose is meant to require NEITHER, so a player already wearing either
     * one can't be put into it. Head restraints are fine either way - see
     * LiePoseEvents' onInteractWithPosedTarget for how arm/leg restraints
     * are also kept from being applied WHILE already posed, and how head
     * restraints are specifically still allowed to be applied/removed.
     *
     * Also sets the actual collision box immediately here (not just next
     * tick) - LiePoseEvents re-asserts it every tick after this to keep it
     * pinned to the locked position, but a movement-blocked player might
     * never trigger any OTHER code path that would naturally recompute it,
     * so the very first application needs to do it explicitly rather than
     * waiting on one.
     */
    public static boolean setPosed(ServerPlayer player, boolean posed) {
        ILiePose cap = get(player);
        if (cap == null) {
            return false;
        }

        if (!posed) {
            cap.setPosed(false);
            cap.setLockedBedPos(null);
            restoreNormalBoundingBox(player);
            player.setNoGravity(false);
            NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                    new LiePoseSyncPacket(player.getId(), false, cap.getLockedYaw(), cap.getLockedSlot(),
                            0.0, 0.0, 0.0));
            return true;
        }

        // Applying: player's own current position/yaw, same as before.
        return applyPosed(player, player.getX(), player.getY(), player.getZ(), player.getYRot(), null);
    }

    /**
     * Same as setPosed(player, true), but the locked position/yaw come from
     * a BED instead of the player's own current stance - used by
     * BedRestraintItem so the body ends up lying along the bed (head on
     * the pillow side) regardless of which way the player happened to be
     * facing when the item was used, rather than wherever they were
     * standing/looking (which is what the /test command still does, and
     * what plain setPosed(player, true) still does for it).
     *
     * footBlockPos is the bed's FOOT half; bedFacing is the bed's own
     * FACING property, which points away from the foot toward the head.
     *
     * NOT fed straight in as lockedYaw: bodyExtendDirection has its own
     * built-in 180-degree offset from whatever yaw it's given (BODY_YAW_
     * OFFSET + RENDER_COMPOUND_BASELINE = 180 total) - confirmed correct
     * for the /test command's own use (lockedYaw = the player's own
     * facing, body extends BACKWARD from that, i.e. the 180-degree flip is
     * exactly right there), but it means feeding in bedFacing directly
     * would extend the body AWAY from the bed (past the foot, into open
     * air) instead of onto it - confirmed in-game (round 12): the player
     * ended up hovering off the end of the bed instead of lying on it.
     * Passing bedFacing.getOpposite() here cancels that built-in flip back
     * out, so the body actually extends foot-to-head as intended.
     *
     * Locked X/Z are NOT simply the foot block's own center - see the
     * centering comment inside the method body for why they're offset
     * slightly toward the foot edge from there. Y is left as the player's
     * own current Y (they're already required to be standing on the bed,
     * so it's already the correct standing-on-this-block height - no need
     * to recompute it from the block itself).
     */
    public static boolean setPosedOnBed(ServerPlayer player, net.minecraft.core.BlockPos footBlockPos,
                                         net.minecraft.core.Direction bedFacing) {
        float lockedYaw = bedFacing.getOpposite().toYRot();

        // The bed spans 2 full blocks along its facing axis (foot block +
        // head block), foot edge at 0, head edge at 2, center at 1.0. The
        // body pivot (feet) sits BODY_LENGTH/2 behind the body's own visual
        // center, so for that center to land on the bed's center (1.0), the
        // pivot itself needs to sit at (1.0 - BODY_LENGTH/2) along that
        // axis, not at the foot block's own center (0.5) - anchoring at
        // the foot block's center instead (round 12's version) put the
        // body visibly too far toward the head end, confirmed in-game.
        // bodyExtendDirection(lockedYaw) is the same "toward head" unit
        // vector the render/camera/hitbox all already use, so shifting the
        // foot-block-center point backward (toward the foot) along that
        // same direction by (0.5 - (1.0 - BODY_LENGTH / 2)) blocks lands
        // exactly on the desired pivot point.
        Vec3 pivot = bedLockedPivot(footBlockPos, lockedYaw);

        return applyPosed(player, pivot.x, player.getY() + BED_VERTICAL_LIFT, pivot.z, lockedYaw, footBlockPos);
    }

    /**
     * Where the body's PIVOT (the feet) has to sit for the lying body to be
     * centred on the bed, given the bed's foot block and the locked yaw. Y is left
     * at zero - the caller supplies it, because the two callers get it from
     * different places (a player's own standing height, plus BED_VERTICAL_LIFT).
     *
     * <p>Extracted in 1.5.11 so the Fake Players path uses this function rather
     * than a copy of it (see fakeplayer/FakeStationaryUtil#tryBed). The body of it
     * is unchanged, and the comment above describes the whole derivation - the
     * point of sharing it is that the FOOT_SIDE_NUDGE and centre-shift values were
     * tuned in game over several rounds, and two copies would eventually disagree.
     */
    public static Vec3 bedLockedPivot(net.minecraft.core.BlockPos footBlockPos, float lockedYaw) {
        Vec3 towardHead = bodyExtendDirection(lockedYaw);
        double centerShift = 0.5 - (1.0 - BODY_LENGTH / 2.0) + FOOT_SIDE_NUDGE;
        double x = footBlockPos.getX() + 0.5 - towardHead.x * centerShift;
        double z = footBlockPos.getZ() + 0.5 - towardHead.z * centerShift;
        return new Vec3(x, 0.0, z);
    }

    private static boolean applyPosed(ServerPlayer player, double x, double y, double z, float yaw,
                                       @Nullable net.minecraft.core.BlockPos bedFootPos) {
        ILiePose cap = get(player);
        if (cap == null) {
            return false;
        }

        IRestrainableCapability restrainable = CuffedAPI.Capabilities.getRestrainableCapability(player);
        if (restrainable.armsRestrained() || restrainable.legsRestrained()) {
            return false;
        }

        // ROUND (this session): [stated] noticed an already-posed target
        // could still be right-clicked with ANOTHER Bed Restraint item -
        // it got consumed and briefly re-locked the (already correct)
        // position, incidentally surfacing the observer-desync bug fixed
        // in LiePoseEvents' onPlayerTick this same round. Genuinely
        // shouldn't have been possible at all: refuse outright (same
        // silent style as the other preconditions here - see
        // BedRestraintItem's tryRestrain, no toast) if already posed,
        // rather than re-running the whole apply sequence and consuming a
        // second item for no reason.
        if (cap.isPosed()) {
            return false;
        }

        // [stated] confirmed the crouch/legs-sinking render bug is still
        // present after multiple real fix attempts (shiftKeyDown clear,
        // Pose.STANDING force, TWO separate PlayerRenderer mixins - one for
        // setupRotations, one for the actual confirmed culprit,
        // getRenderOffset returning a hardcoded -0.125 Y offset while
        // isCrouching() - see git history / old memory notes if this is
        // ever revisited) and asked to stop chasing it. All of those
        // attempts have been removed; this precondition is the accepted
        // permanent behavior now, not a temporary workaround - refuses
        // outright (silently - see BedRestraintItem's tryRestrain, no
        // toast) rather than allowing the broken visual at all.
        if (player.isCrouching()) {
            return false;
        }

        cap.setPosed(true);
        cap.setLockedPos(x, y, z);
        cap.setLockedYaw(yaw);
        cap.setLockedSlot(player.getInventory().selected);
        cap.setLockedBedPos(bedFootPos);
        player.setDeltaMovement(Vec3.ZERO);
        player.setNoGravity(true);
        player.setBoundingBox(buildLieBoundingBox(x, y, z, yaw));

        // [stated] noticed the restrained player starts out facing whichever
        // way they happened to be looking the moment the restraint was
        // applied, rather than facing along the bed. Snapping all three yaw
        // fields to the locked yaw here makes the starting look direction
        // match the bed instead of being leftover state - mouse look stays
        // free afterward exactly as before, this only affects the initial
        // pose. Kept independently of the crouch-bug revert above since it's
        // a separate, working, unrelated improvement - say so if this isn't
        // wanted either.
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.setYBodyRot(yaw);

        NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new LiePoseSyncPacket(player.getId(), true, yaw, cap.getLockedSlot(), x, y, z));
        return true;
    }
}
