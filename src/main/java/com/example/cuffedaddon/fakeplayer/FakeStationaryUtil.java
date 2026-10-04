package com.example.cuffedaddon.fakeplayer;

import com.example.cuffedaddon.CuffedAddon;
import com.example.cuffedaddon.blocks.WallRestraintBlock;
import com.example.cuffedaddon.capability.ModCapabilities;
import com.example.cuffedaddon.network.FakeDetainedSyncPacket;
import com.example.cuffedaddon.network.LiePoseSyncPacket;
import com.example.cuffedaddon.network.NetworkHandler;
import com.example.cuffedaddon.network.WallPoseSyncPacket;
import com.example.cuffedaddon.pose.ILiePose;
import com.example.cuffedaddon.pose.IWallPose;
import com.example.cuffedaddon.pose.LiePoseUtil;
import com.example.cuffedaddon.pose.WallPoseUtil;
import com.lazrproductions.cuffed.blocks.PilloryBlock;
import com.lazrproductions.cuffed.restraints.base.RestraintType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The three STATIONARY restraints on a Fake Players fake player - Cuffed's
 * pillory, and this addon's own Bed Restraint and Wall Restraint - in one place.
 *
 * <h2>How this differs from the 1.5.x work it follows</h2>
 * The three body-slot restraints needed a parallel representation of their own
 * ({@code IFakeRestrained}) because Cuffed's is unreachable on a mob. Two of these
 * three do NOT: the Bed Restraint and the Wall Restraint are <b>this addon's</b>
 * features, and their state already lives in plain Forge capabilities
 * ({@code ILiePose}, {@code IWallPose}) rather than in {@code Player}-keyed synched
 * data. So they are reused directly - the same capability, the same sync packets,
 * the same render mixins, the same cosmetic cuffs layer - with nothing duplicated.
 * All that was needed on that side was widening the READ paths from {@code Player}
 * to {@code LivingEntity} (a fake player is a {@code LivingEntity}; every call
 * site already passed something narrower) and attaching the two providers to their
 * entity as well as to players.
 *
 * <p>That matters for more than tidiness. The lie pose is the most delicate
 * rendering in this mod - a whole-body rotation plus a limb pose plus a hand-built
 * collision box, settled over roughly twenty rounds of in-game testing. Rebuilding
 * any of it for a second entity type would have re-opened every one of those
 * questions. Reusing it means a bed-restrained fake player is posed by exactly the
 * confirmed-correct code that poses a bed-restrained player.
 *
 * <p>The pillory is the one that could not be reused, because it is CUFFED's
 * feature and its state is in {@code Player}-keyed {@code EntityDataAccessor}s -
 * see {@link IFakeDetained} for why that is a hard wall. That one gets a parallel
 * capability, but even there the POSE is Cuffed's own
 * {@code HumanoidAnimationHelper#animatePilloryDetainedAnimation} (it takes a
 * {@code LivingEntity}, not a {@code Player}), so the pose is identical by
 * construction rather than by eye - the same trick that made the arm pose work in
 * 1.5.2.
 *
 * <h2>[stated]'s spec</h2>
 * These render NO job possible and are otherwise purely cosmetic. Both fall out of
 * the pieces already here: {@code FakePlayerImmobilizeGoal} and
 * {@code FakePlayerJobMixin} both consult {@link #isStationary} alongside the leg
 * restraint they already consulted.
 */
public final class FakeStationaryUtil {

    /**
     * How close to the pillory's own "position behind" a fake player has to be
     * standing, in blocks, measured horizontally. <b>0.3 is Cuffed's number, not
     * one chosen here</b> - {@code PilloryBlock#attemptToToggleDetained} and
     * {@code #getDetainedEntity} both test {@code dist < 0.3f} against the same
     * point, so using anything else would mean a fake player and a real player
     * needed to be stood differently to be caught by the same block.
     */
    private static final double PILLORY_SNAP_DISTANCE = 0.3;

    /** Anything further than this from the locked spot gets pushed back. Mirrors the pose events' own threshold. */
    private static final double DRIFT_EPSILON_SQUARED = 1.0E-4;

    private FakeStationaryUtil() {
    }

    // ------------------------------------------------------------------ state

    @Nullable
    public static IFakeDetained detained(LivingEntity entity) {
        return entity.getCapability(ModCapabilities.FAKE_DETAINED).orElse(null);
    }

    public static boolean isDetained(LivingEntity entity) {
        IFakeDetained cap = detained(entity);
        return cap != null && cap.isDetained();
    }

    /**
     * True if this fake player is held by ANY of the three stationary restraints.
     *
     * <p>This is the single predicate the rest of the feature is built on - the
     * immobilise goal, the job pause, the look goal, the target block and the
     * mutual exclusion between the three all ask this one question rather than
     * each enumerating the three states. Same instinct as
     * {@code FakePlayerJobRules}: put the rule in one place so a fourth
     * stationary restraint would only have to be added here.
     */
    public static boolean isStationary(LivingEntity entity) {
        return isDetained(entity) || LiePoseUtil.isPosed(entity) || WallPoseUtil.isPosed(entity);
    }

    /**
     * Whether a stationary restraint may be applied at all.
     *
     * <h2>Why arms and legs block it</h2>
     * Both of the addon's own pose applies already refuse a target wearing an arm
     * or leg restraint ({@code LiePoseUtil#applyPosed},
     * {@code WallPoseUtil#tryApply}), and Cuffed's pillory refuses one wearing an
     * ARM restraint ({@code PilloryBlock#canDetainPlayer} - {@code
     * cap.armsRestrained()} returns false). Legs are included here for all three
     * rather than only for the two that already had it, because the reason the
     * addon's poses refuse is a rendering one that applies just as much to a
     * pilloried entity: Cuffed's real restraint layer copies whatever pose is in
     * effect into its own restraint model and draws real cuffs on top of it, which
     * on a spread or folded pose lands the cuffs somewhere the limbs are not. A
     * head restraint is fine with all three, exactly as it is for a real player.
     */
    public static boolean canApplyStationary(LivingEntity entity) {
        if (isStationary(entity)) {
            return false;
        }
        IFakeRestrained cap = FakePlayerRestraintUtil.get(entity);
        return cap == null || !(cap.has(RestraintType.Arm) || cap.has(RestraintType.Leg));
    }

    // ---------------------------------------------------------------- pillory

    /**
     * The fake player standing at the pillory's own "position behind", or null.
     *
     * <p>The distance test is Cuffed's, verbatim: horizontal only (x/z), against
     * {@code PilloryBlock#getPositionBehind}, under {@link #PILLORY_SNAP_DISTANCE}.
     * The search box is a deliberately loose 1.5-block cube around that point
     * purely to keep the candidate list small - it is not the acceptance test, the
     * distance check below is.
     */
    @Nullable
    public static LivingEntity findFakePlayerBehindPillory(Level level, Vec3 behind) {
        AABB box = new AABB(behind.x - 1.5, behind.y - 1.5, behind.z - 1.5,
                behind.x + 1.5, behind.y + 1.5, behind.z + 1.5);
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (!FakePlayerSupport.isFakePlayer(candidate)) {
                continue;
            }
            double dx = behind.x - candidate.position().x;
            double dz = behind.z - candidate.position().z;
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance < PILLORY_SNAP_DISTANCE && distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * Locks a fake player into the pillory. {@code anchorPos} is the pillory's
     * UPPER half; {@code behind} and {@code facingYaw} are Cuffed's own
     * {@code getPositionBehind} and {@code getFacingRotation} for that block, so
     * the entity ends up at the same spot and facing the same way a real player
     * would.
     */
    public static boolean tryDetain(LivingEntity target, BlockPos anchorPos, Vec3 behind, float facingYaw) {
        IFakeDetained cap = detained(target);
        if (cap == null || !canApplyStationary(target)) {
            return false;
        }

        cap.setDetained(true);
        cap.setLockedPos(behind.x, behind.y, behind.z);
        cap.setLockedYaw(facingYaw);
        cap.setAnchorPos(anchorPos);

        target.setDeltaMovement(Vec3.ZERO);
        target.setNoGravity(true);
        target.moveTo(behind.x, behind.y, behind.z, facingYaw, target.getXRot());
        target.setYBodyRot(facingYaw);
        target.setYHeadRot(facingYaw);
        stopNavigation(target);
        forceStanding(target);

        syncDetained(target, cap);
        return true;
    }

    public static boolean undetain(LivingEntity target) {
        IFakeDetained cap = detained(target);
        if (cap == null || !cap.isDetained()) {
            return false;
        }
        cap.setDetained(false);
        cap.setAnchorPos(null);
        restoreGravity(target);
        restorePose(target);
        syncDetained(target, cap);
        return true;
    }

    // -------------------------------------------------------------------- bed

    /**
     * Bed Restraint on a fake player. Geometry is
     * {@code LiePoseUtil#bedLockedPivot} - the same function the real-player path
     * uses, not a copy of it - so the body lands along the bed with the head on the
     * pillow side identically.
     *
     * <p>Unlike the player path this does not capture a hotbar slot (a fake player
     * has no hotbar to lock) and does not teleport through a connection, because
     * there is none - a plain {@code moveTo} is the server-authoritative move for a
     * mob and reaches observers through normal entity tracking.
     */
    public static boolean tryBed(LivingEntity target, BlockPos footBlockPos, Direction bedFacing) {
        ILiePose cap = LiePoseUtil.get(target);
        if (cap == null || !canApplyStationary(target)) {
            return false;
        }

        float lockedYaw = bedFacing.getOpposite().toYRot();
        Vec3 pivot = LiePoseUtil.bedLockedPivot(footBlockPos, lockedYaw);
        double x = pivot.x;
        double y = target.getY() + LiePoseUtil.BED_VERTICAL_LIFT;
        double z = pivot.z;

        cap.setPosed(true);
        cap.setLockedPos(x, y, z);
        cap.setLockedYaw(lockedYaw);
        cap.setLockedSlot(0);
        cap.setLockedBedPos(footBlockPos.immutable());

        target.setDeltaMovement(Vec3.ZERO);
        target.setNoGravity(true);
        target.moveTo(x, y, z, lockedYaw, target.getXRot());
        target.setYBodyRot(lockedYaw);
        target.setYHeadRot(lockedYaw);
        target.setBoundingBox(LiePoseUtil.buildLieBoundingBox(x, y, z, lockedYaw));
        stopNavigation(target);
        forceStanding(target);

        NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target),
                new LiePoseSyncPacket(target.getId(), true, lockedYaw, 0, x, y, z));
        return true;
    }

    public static boolean releaseBed(LivingEntity target) {
        ILiePose cap = LiePoseUtil.get(target);
        if (cap == null || !cap.isPosed()) {
            return false;
        }
        cap.setPosed(false);
        cap.setLockedBedPos(null);
        LiePoseUtil.restoreNormalBoundingBox(target);
        restoreGravity(target);
        restorePose(target);

        NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target),
                new LiePoseSyncPacket(target.getId(), false, cap.getLockedYaw(), 0,
                        target.getX(), target.getY(), target.getZ()));
        return true;
    }

    // ------------------------------------------------------------------- wall

    /**
     * The fake player standing at a Wall Restraint panel's base, or null. Same
     * plain column match {@code WallPoseUtil#findStandingPlayer} uses for a real
     * player - see its doc for why it is a column match rather than a shape
     * overlap test.
     */
    @Nullable
    public static LivingEntity findStandingFakePlayer(Level level, BlockPos lowerPos) {
        AABB searchBox = new AABB(lowerPos.getX(), lowerPos.getY(), lowerPos.getZ(),
                lowerPos.getX() + 1.0, lowerPos.getY() + 2.0, lowerPos.getZ() + 1.0);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, searchBox);
        for (LivingEntity candidate : candidates) {
            if (FakePlayerSupport.isFakePlayer(candidate) && candidate.blockPosition().equals(lowerPos)) {
                return candidate;
            }
        }
        return null;
    }

    public static boolean tryWall(LivingEntity target, BlockPos primaryPos, Direction facing) {
        IWallPose cap = WallPoseUtil.get(target);
        if (cap == null || !canApplyStationary(target)) {
            return false;
        }

        float yaw = facing.toYRot();
        Vec3 locked = WallPoseUtil.wallLockedPos(primaryPos, facing);

        cap.setPosed(true);
        cap.setLockedPos(locked.x, locked.y, locked.z);
        cap.setLockedYaw(yaw);
        cap.setLockedSlot(0);
        cap.setLockedPrimaryPos(primaryPos.immutable());

        target.setDeltaMovement(Vec3.ZERO);
        target.setNoGravity(true);
        target.moveTo(locked.x, locked.y, locked.z, yaw, target.getXRot());
        target.setYBodyRot(yaw);
        target.setYHeadRot(yaw);
        stopNavigation(target);
        forceStanding(target);

        NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target),
                new WallPoseSyncPacket(target.getId(), true, yaw, 0, locked.x, locked.y, locked.z));
        return true;
    }

    public static boolean releaseWall(LivingEntity target) {
        IWallPose cap = WallPoseUtil.get(target);
        if (cap == null || !cap.isPosed()) {
            return false;
        }
        WallPoseUtil.unoccupyBlock(target.level(), cap.getLockedPrimaryPos());

        cap.setPosed(false);
        cap.setLockedPrimaryPos(null);
        restoreGravity(target);
        restorePose(target);

        NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target),
                new WallPoseSyncPacket(target.getId(), false, cap.getLockedYaw(), 0,
                        target.getX(), target.getY(), target.getZ()));
        return true;
    }

    /**
     * Frees whichever fake player is locked to the panel at {@code primaryPos}.
     * Called from {@code WallRestraintBlock#onRemove} alongside
     * {@code WallPoseUtil#releaseWhoeverIsAt}, rather than folded into that method,
     * so the {@code pose} package keeps knowing nothing about fake players.
     *
     * <p>The search is bounded to the panel's own neighbourhood instead of scanning
     * the level: whoever is locked here is standing in that cell by construction.
     */
    public static void releaseWallWhoeverIsAt(Level level, BlockPos primaryPos) {
        if (!FakePlayerSupport.isModLoaded()) {
            return;
        }
        AABB box = new AABB(primaryPos.getX() - 2.0, primaryPos.getY() - 2.0, primaryPos.getZ() - 2.0,
                primaryPos.getX() + 3.0, primaryPos.getY() + 4.0, primaryPos.getZ() + 3.0);
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (!FakePlayerSupport.isFakePlayer(candidate)) {
                continue;
            }
            IWallPose cap = WallPoseUtil.get(candidate);
            if (cap != null && cap.isPosed() && primaryPos.equals(cap.getLockedPrimaryPos())) {
                releaseWall(candidate);
                return;
            }
        }
    }

    // ------------------------------------------------------------------- tick

    /**
     * Holds a stationary fake player where it belongs, and lets it go if the block
     * holding it is gone.
     *
     * <h2>Why the anchor check is unconditional, unlike Cuffed's</h2>
     * Cuffed does the equivalent check for a real player, but nests it inside
     * {@code ALLOW_BREAKING_OUT_OF_PILLORY} - so with that config off, breaking the
     * pillory leaves the player detained to an air block. For a real player that is
     * recoverable (an operator can {@code /cuffed} them free). A fake player cannot
     * be freed by any command, so the same behaviour would mean a permanently
     * frozen entity with no way out short of killing it. The check runs here
     * regardless of any config.
     *
     * <h2>Why the three are checked in order and only one acts</h2>
     * {@link #canApplyStationary} makes them mutually exclusive on the way in, so
     * in practice at most one is ever set. The order is Cuffed's own precedence
     * anyway (its {@code HumanoidModelMixin} checks {@code detained} before any arm
     * pose), and returning after the first keeps a corrupted save that somehow has
     * two set from fighting itself every tick.
     */
    public static void tick(LivingEntity fake) {
        Level level = fake.level();
        if (level.isClientSide()) {
            return;
        }

        IFakeDetained detainedCap = detained(fake);
        if (detainedCap != null && detainedCap.isDetained()) {
            BlockPos anchor = detainedCap.getAnchorPos();
            if (anchor == null || !(level.getBlockState(anchor).getBlock() instanceof PilloryBlock)) {
                undetain(fake);
            } else {
                hold(fake, detainedCap.getLockedX(), detainedCap.getLockedY(), detainedCap.getLockedZ(),
                        detainedCap.getLockedYaw());
                return;
            }
        }

        ILiePose lie = LiePoseUtil.get(fake);
        if (lie != null && lie.isPosed()) {
            BlockPos bedPos = lie.getLockedBedPos();
            if (bedPos != null && !isBed(level, bedPos, fake)) {
                releaseBed(fake);
            } else {
                hold(fake, lie.getLockedX(), lie.getLockedY(), lie.getLockedZ(), lie.getLockedYaw());
                // Re-asserted every tick for the same reason the player path does
                // it: nothing else recomputes the box for an entity that can no
                // longer move, and the box has to reach toward where the head
                // actually lands once the model is laid flat.
                fake.setBoundingBox(LiePoseUtil.buildLieBoundingBox(
                        lie.getLockedX(), lie.getLockedY(), lie.getLockedZ(), lie.getLockedYaw()));
                return;
            }
        }

        IWallPose wall = WallPoseUtil.get(fake);
        if (wall != null && wall.isPosed()) {
            BlockPos primaryPos = wall.getLockedPrimaryPos();
            if (primaryPos == null || !(level.getBlockState(primaryPos).getBlock() instanceof WallRestraintBlock)) {
                releaseWall(fake);
            } else {
                hold(fake, wall.getLockedX(), wall.getLockedY(), wall.getLockedZ(), wall.getLockedYaw());
            }
        }
    }

    /**
     * Releases every stationary restraint on this fake player, used on death.
     *
     * <p>Nothing is handed back: the Bed Restraint item is returned by the key
     * release path (mirroring how Cuffed returns a restraint on unequip but not on
     * a break), the Wall Restraint and the pillory are blocks that stay where they
     * are, and {@code FakePlayerRestraintUtil#dropAllOnDeath} already covers the
     * body-slot restraints. The point of this is only to leave no state behind on a
     * dying entity - and, for the wall, to put its panel back to unoccupied so it
     * can be used again.
     */
    public static void releaseAll(LivingEntity fake) {
        undetain(fake);
        releaseBed(fake);
        releaseWall(fake);
    }

    /**
     * Sends one watcher the current stationary state, for the same reason
     * {@code FakePlayerEvents#onStartTracking} sends the restraint state: someone
     * who walks into range after the fact never saw the broadcast that set it, and
     * capabilities do not sync on their own.
     */
    public static void syncTo(ServerPlayer watcher, LivingEntity fake) {
        IFakeDetained detainedCap = detained(fake);
        if (detainedCap != null && detainedCap.isDetained()) {
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> watcher),
                    new FakeDetainedSyncPacket(fake.getId(), detainedCap.serializeNBT()));
        }

        ILiePose lie = LiePoseUtil.get(fake);
        if (lie != null && lie.isPosed()) {
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> watcher),
                    new LiePoseSyncPacket(fake.getId(), true, lie.getLockedYaw(), 0,
                            lie.getLockedX(), lie.getLockedY(), lie.getLockedZ()));
        }

        IWallPose wall = WallPoseUtil.get(fake);
        if (wall != null && wall.isPosed()) {
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> watcher),
                    new WallPoseSyncPacket(fake.getId(), true, wall.getLockedYaw(), 0,
                            wall.getLockedX(), wall.getLockedY(), wall.getLockedZ()));
        }
    }

    // ----------------------------------------------------------------- shared

    private static void hold(LivingEntity fake, double x, double y, double z, float yaw) {
        fake.setDeltaMovement(Vec3.ZERO);
        fake.fallDistance = 0.0F;
        fake.setYRot(yaw);
        fake.setYBodyRot(yaw);
        fake.setYHeadRot(yaw);
        fake.yBodyRotO = yaw;

        double dx = x - fake.getX();
        double dy = y - fake.getY();
        double dz = z - fake.getZ();
        if ((dx * dx + dy * dy + dz * dz) > DRIFT_EPSILON_SQUARED) {
            fake.setPos(x, y, z);
        }
    }

    /**
     * Forces the fake player to STANDING and remembers what it was, so a stationary
     * restraint's own pose is the only one in effect - [stated]'s request at 1.5.11:
     * <i>"make it so that poses are disabled in the stationary restraints"</i>.
     *
     * <p>Their pose offsets are ADDED on top of whatever pose this addon sets rather
     * than replacing it (see {@code FakePlayerSittingPoseMixin}), which is exactly
     * what makes handcuffs-while-sitting work and exactly what makes
     * pilloried-while-sitting look like neither. Forcing STANDING is the narrow fix;
     * {@code FakePlayerPhysicalStateMixin} refuses any further change while the
     * restraint is on, so their menu cannot put it back.
     *
     * <p>The previous pose is saved rather than discarded - a fake player you posed
     * sitting, pilloried and then released should sit back down, not silently lose
     * the pose you gave it.
     */
    private static void forceStanding(LivingEntity fake) {
        if (!(fake instanceof IFakePlayerPhysicalState pose)) {
            // Their mixin did not apply. Already reported loudly at entity load; the
            // restraint still works, its pose just coexists with theirs as before.
            return;
        }
        IFakeDetained cap = detained(fake);
        if (cap != null) {
            cap.setSavedPhysicalState(pose.cuffedaddon$getPhysicalState());
        }
        pose.cuffedaddon$setPhysicalState(IFakePlayerPhysicalState.STANDING);
    }

    /** Hands back the pose {@link #forceStanding} took away, once nothing is holding the entity. */
    private static void restorePose(LivingEntity fake) {
        if (isStationary(fake) || !(fake instanceof IFakePlayerPhysicalState pose)) {
            return;
        }
        IFakeDetained cap = detained(fake);
        if (cap != null) {
            pose.cuffedaddon$setPhysicalState(cap.getSavedPhysicalState());
        }
    }

    private static void stopNavigation(LivingEntity fake) {
        if (fake instanceof Mob mob) {
            mob.getNavigation().stop();
        }
    }

    /**
     * Gravity comes back only when NOTHING else is still holding this entity -
     * releasing one of three restraints must not un-pin it for the other two. The
     * three are mutually exclusive on apply, so in practice this is always "yes",
     * but expressing it as a question rather than an assumption is what makes it
     * safe to add a fourth later.
     */
    private static void restoreGravity(LivingEntity fake) {
        if (!isStationary(fake)) {
            fake.setNoGravity(false);
        }
    }

    private static boolean isBed(Level level, BlockPos pos, LivingEntity target) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock().isBed(state, level, pos, target);
    }

    private static void syncDetained(LivingEntity target, IFakeDetained cap) {
        if (target.level().isClientSide()) {
            return;
        }
        NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target),
                new FakeDetainedSyncPacket(target.getId(), cap.serializeNBT()));
    }

    private static boolean reportedFirstApply;

    /** One line the first time any of the three lands, same purpose as the other diagnostics in this package. */
    public static void reportFirstApply(String which, boolean applied) {
        if (reportedFirstApply || !applied) {
            return;
        }
        reportedFirstApply = true;
        CuffedAddon.LOGGER.info("Fake Players compat: stationary restraint applied to a fake player ({}).", which);
    }
}
