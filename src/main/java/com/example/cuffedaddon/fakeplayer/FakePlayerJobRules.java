package com.example.cuffedaddon.fakeplayer;

import com.lazrproductions.cuffed.restraints.base.RestraintType;

import javax.annotation.Nullable;

/**
 * [stated]'s job-gating spec for a restrained fake player, in one place.
 *
 * <pre>
 *   legs restrained  -> NO job runs at all. Immobilised.
 *   arms restrained  -> only None, Idle, Follow.
 *   head restrained  -> only None, Idle, Guard, Follow.
 *   nothing          -> every job, untouched.
 * </pre>
 *
 * <h2>Why arms exclude Guard</h2>
 * [stated] said: <i>"Im not sure if the guard job is supposed to attack mobs, but
 * if it is then Id want arm restraints to disable that job aswell."</i> It does.
 * {@code GuardJobExecutor#tick} scans for hostiles with {@code findHostile} and
 * calls {@code setTarget}, and the entity registers {@code MeleeAttackGoal},
 * {@code FakeRangedAttackGoal} and {@code FakeHurtByTargetGoal}. So guard is a
 * combat job and bound arms rule it out.
 *
 * <h2>Why head restraints still allow everything in the base set</h2>
 * Also [stated]: <i>"head restraints will simply be visual, as this isnt a real
 * person who needs to see."</i> So a blindfolded fake player is not impaired at
 * all; the head slot only narrows the job list to the four that are allowed while
 * wearing anything, which is the separate blanket rule
 * (<i>"any restraint should disable all jobs except idle, guard, follow and of
 * course, none"</i>).
 *
 * <h2>THE FRAGILE PART - read before updating Fake Players</h2>
 * The job is read as an <b>ordinal</b> out of their synched {@code AIState}
 * compound (they store {@code putInt("Job", job.ordinal())} and nothing else), so
 * these constants are positional and are coupled to the declaration order of
 * {@code dev.duzo.players.entities.ai.Job} as it stands in 2.2.0:
 * <pre>
 *   0 NONE   1 IDLE   2 GUARD  3 FOLLOW  4 PATROL  5 DEPOSIT
 *   6 COURIER 7 MINER 8 LUMBERJACK 9 FISHERMAN 10 FARMER 11 CRAFTER
 * </pre>
 * If a future version of that mod <b>inserts</b> a job rather than appending one,
 * every value above it shifts and this gating silently starts allowing and
 * blocking the wrong jobs. There is no name in the tag to validate against, so
 * this cannot be made self-checking. <b>Re-verify this enum's order whenever Fake
 * Players updates.</b> Appending new jobs at the end is safe - anything unknown
 * falls outside the allow-lists and is therefore blocked while restrained, which
 * is the safe direction.
 */
public final class FakePlayerJobRules {

    public static final int JOB_NONE = 0;
    public static final int JOB_IDLE = 1;
    public static final int JOB_GUARD = 2;
    public static final int JOB_FOLLOW = 3;

    /** The tag key their AIState writes the job ordinal under. */
    public static final String TAG_JOB = "Job";

    private FakePlayerJobRules() {
    }

    /**
     * Whether a job may run given what the fake player is wearing.
     *
     * @param jobOrdinal the ordinal straight out of their AIState tag.
     * @param cap        the fake player's restraint state, or null when it has none.
     */
    public static boolean jobAllowed(int jobOrdinal, @Nullable IFakeRestrained cap) {
        if (cap == null || !cap.isRestrained()) {
            return true;
        }
        // Legs first: it is the strictest and short-circuits everything else.
        if (cap.has(RestraintType.Leg)) {
            return false;
        }
        if (cap.has(RestraintType.Arm)) {
            return jobOrdinal == JOB_NONE || jobOrdinal == JOB_IDLE || jobOrdinal == JOB_FOLLOW;
        }
        // Head only, or any other combination that isn't arms or legs.
        return jobOrdinal == JOB_NONE || jobOrdinal == JOB_IDLE
                || jobOrdinal == JOB_GUARD || jobOrdinal == JOB_FOLLOW;
    }

    /** Legs bound means it does not move under its own power at all. */
    public static boolean immobilised(@Nullable IFakeRestrained cap) {
        return cap != null && cap.has(RestraintType.Leg);
    }
}
