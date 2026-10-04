package com.example.cuffedaddon.fakeplayer;

import com.lazrproductions.cuffed.restraints.base.RestraintType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

import java.util.EnumSet;

/**
 * Keeps a leg-restrained fake player awake: it still turns its head, and still
 * turns to face you, it just cannot go anywhere.
 *
 * <h2>Why this is needed at all, when nothing blocks LOOK</h2>
 * {@code FakePlayerImmobilizeGoal} deliberately claims only {@code MOVE}, so
 * every {@code LOOK} goal they register is still free to run. The trouble is what
 * those goals actually are. Almost all of their liveliness comes from goals that
 * claim {@code MOVE} <i>and</i> {@code LOOK} together - {@code FollowOwnerGoal},
 * {@code MoveTowardsItemsGoal}, the strolling - so blocking MOVE takes their
 * looking with it. What is left is vanilla's {@code RandomLookAroundGoal}, which
 * starts on a 2% roll per tick and then looks at a point ONE block away in a
 * random direction: a few degrees of head movement, occasionally. Add that to a
 * body that never turns, because a mob's body only follows its head once the head
 * has swung far enough, and the result is a statue. That is the reported "leg
 * restraints completely freeze the fake player" - not a blocked flag, an empty
 * one.
 *
 * <h2>What it does instead</h2>
 * While immobilised it watches the nearest player within {@value #RANGE} blocks,
 * and with nobody around it looks at a point {@value #IDLE_LOOK_DISTANCE} blocks
 * away in a random direction every couple of seconds. The distance is the part
 * that matters: a far target moves the head through a real angle, the head drags
 * the body round behind it through vanilla's own
 * {@code BodyRotationControl#rotateBodyIfNecessary}, and the fake player visibly
 * turns to follow you while its feet stay put. That is exactly "still be able to
 * turn around and move their head, just not to be able to physically move".
 *
 * <h2>Scope</h2>
 * Legs bound and head free. A head restraint turns this off entirely - see
 * {@link #canUse()} for why, and for what that leaves behind.
 *
 * <h2>Why it cannot affect an unrestrained fake player</h2>
 * {@link #canUse()} is false unless the legs are bound, so an unrestrained fake
 * player never sees this goal run and its own looking behaviour is untouched -
 * the same "must not interfere with their customization" rule the interact
 * listener follows.
 */
public class FakePlayerRestrainedLookGoal extends Goal {

    /** How far away a player can be and still be watched. */
    public static final float RANGE = 8.0f;

    /**
     * How far away the idle look target is placed.
     *
     * <p>Not one block, which is what {@code RandomLookAroundGoal} uses and which
     * barely moves the head. Eight blocks makes the same random bearing a real
     * turn, which is what lets the body follow.
     */
    public static final double IDLE_LOOK_DISTANCE = 8.0d;

    /**
     * How often the nearest player is actually searched for, in ticks.
     *
     * <h2>What this costs, and why it is still worth throttling</h2>
     * {@code Level#getNearestPlayer} walks the level's PLAYER LIST and does a
     * squared-distance comparison per entry - it is not an area search over
     * entities, so on any normal server it is a handful of subtractions. Running
     * it every tick for every leg-restrained fake player was never going to be
     * measurable next to the per-frame renderer rebuild on the client side.
     *
     * <p>It is throttled anyway because there is no reason not to: who the nearest
     * player is cannot meaningfully change in three ticks, while {@code setLookAt}
     * still runs every tick against the remembered player, so the head tracks just
     * as smoothly. Same result, a quarter of the scans.
     */
    private static final int SCAN_INTERVAL = 4;

    private static final float MAX_YAW_STEP = 30.0f;
    private static final float MAX_PITCH_STEP = 30.0f;

    private final Mob mob;

    @Nullable
    private Player watching;
    private int ticksUntilScan;

    private double idleX;
    private double idleZ;
    private int idleTicksLeft;

    public FakePlayerRestrainedLookGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.LOOK));
    }

    /**
     * Legs bound, and nothing on the head.
     *
     * <h2>Why a head restraint switches this off entirely</h2>
     * A blindfolded fake player tracking you around the room is wrong, and it is
     * the one place where "head restraints are purely visual" stops being the
     * right call. With anything on its head this goal simply does not run, which
     * hands looking straight back to Fake Players' own {@code RandomLookAroundGoal}
     * - so a head-restrained fake player behaves exactly as it did before any of
     * this existed, rather than getting some third behaviour invented for it.
     *
     * <p>Note what that means in practice: blindfold a leg-restrained fake player
     * and it goes back to being nearly still, because that idle goal only looks
     * one block away. That is the original behaviour, and it reads correctly for
     * something that cannot see.
     */
    @Override
    public boolean canUse() {
        IFakeRestrained cap = FakePlayerRestraintUtil.get(mob);
        if (cap == null) {
            return false;
        }
        // 1.5.11: never while stationary. All three stationary restraints hold the
        // head as part of their pose - the pillory's is Cuffed's own animation,
        // which hard-sets yaw/head/body rotation every frame, and the bed and wall
        // poses fix the head in the model - so a goal turning the head as well
        // would be fighting the pose it is meant to be held in.
        if (FakeStationaryUtil.isStationary(mob)) {
            return false;
        }
        return FakePlayerJobRules.immobilised(cap) && !cap.has(RestraintType.Head);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        idleTicksLeft = 0;
        ticksUntilScan = 0;
        watching = null;
    }

    @Override
    public void stop() {
        // Dropped so a player who has walked away, died or logged out is not held
        // by this goal until the next time it starts.
        watching = null;
    }

    @Override
    public void tick() {
        if (--ticksUntilScan <= 0) {
            watching = mob.level().getNearestPlayer(mob, RANGE);
            ticksUntilScan = SCAN_INTERVAL;
        } else if (watching != null && (!watching.isAlive() || watching.isRemoved()
                || watching.distanceToSqr(mob) > RANGE * RANGE)) {
            // Between scans, still notice the obvious ways a remembered player
            // stops being a valid thing to look at.
            watching = null;
        }

        // A player in range ALWAYS wins. The idle look is only what happens when
        // there is nobody to watch, and its countdown is reset here so that when
        // someone does leave, a fresh direction is picked rather than an old one
        // resumed mid-way.
        if (watching != null) {
            mob.getLookControl().setLookAt(watching, MAX_YAW_STEP, MAX_PITCH_STEP);
            idleTicksLeft = 0;
            return;
        }

        if (--idleTicksLeft <= 0) {
            double bearing = mob.getRandom().nextDouble() * (Math.PI * 2.0d);
            idleX = Math.cos(bearing) * IDLE_LOOK_DISTANCE;
            idleZ = Math.sin(bearing) * IDLE_LOOK_DISTANCE;
            idleTicksLeft = 40 + mob.getRandom().nextInt(60);
        }
        mob.getLookControl().setLookAt(mob.getX() + idleX, mob.getEyeY(), mob.getZ() + idleZ);
    }
}
