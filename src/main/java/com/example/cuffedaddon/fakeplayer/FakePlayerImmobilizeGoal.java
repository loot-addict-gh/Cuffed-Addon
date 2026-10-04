package com.example.cuffedaddon.fakeplayer;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Holds a leg-restrained fake player still, with no mixin required.
 *
 * <h2>Why a Goal rather than a movement override</h2>
 * [stated]'s spec is that leg restraints "immobilize the fake player, rendering no
 * job possible". Their entity is an ordinary {@code PathfinderMob} whose movement
 * all comes from ordinary vanilla goals - stroll, melee attack, move-towards-items,
 * follow-owner, tempt, open-door. A goal registered at priority 0 that claims
 * {@link Goal.Flag#MOVE} and {@link Goal.Flag#JUMP} and is always runnable while
 * the legs are bound therefore blocks every one of them by vanilla's own
 * arbitration, present and future, without touching their code at all. That is the
 * same "find the single upstream dispatch point" instinct that made one small
 * Mixin cover every Cuffed restraint overlay back in 1.3.x.
 *
 * <p>It deliberately does NOT claim {@link Goal.Flag#LOOK}: a bound fake player
 * should still be able to turn its head and watch you, exactly as a restrained
 * real player still has free look.
 *
 * <p>It deliberately does NOT claim {@link Goal.Flag#JUMP} either, which it did
 * until 1.5.6. Now that it registers at a priority that outranks everything Fake
 * Players has, claiming JUMP would take that flag off their {@code FloatGoal} -
 * the goal that keeps a mob swimming at the surface - and a leg-restrained fake
 * player pushed into water would sink and drown. Holding MOVE is enough to stop
 * it going anywhere; letting FloatGoal keep JUMP only lets it stay alive where it
 * already is.
 *
 * <p>The job executors are a separate channel that does not go through goals at
 * all, so they are stopped separately - see {@code FakePlayerEntityMixin}.
 */
public class FakePlayerImmobilizeGoal extends Goal {

    /**
     * The priority this goal must be registered at - see {@code FakePlayerEvents}.
     *
     * <h2>Why it cannot be 0, which is what 1.5.5 used</h2>
     * Vanilla's {@code GoalSelector} only lets a goal take a flag off another goal
     * when {@code WrappedGoal#canBeReplacedBy} says so, and that test is
     * <b>strictly</b> less-than:
     * <pre>
     *   return this.isInterruptable() &amp;&amp; other.getPriority() &lt; this.getPriority();
     * </pre>
     * Fake Players registers {@code FollowOwnerGoal} at priority <b>0</b> holding
     * {@code MOVE} and {@code LOOK}, and its goals are registered before this one
     * is added on entity join, so it is evaluated first each tick. At an equal
     * priority of 0 this goal could never take MOVE from it - a leg-restrained
     * fake player on the follow job simply kept walking, which is exactly what was
     * reported. Minus one is the smallest number that wins, and it wins against
     * every goal they have, since 0 is the lowest they use.
     */
    public static final int PRIORITY = -1;

    private final Mob mob;

    public FakePlayerImmobilizeGoal(Mob mob) {
        this.mob = mob;
        // MOVE only - deliberately NOT JUMP; see the class doc.
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        // 1.5.11: the three STATIONARY restraints (pillory, Bed, Wall) hold a fake
        // player just as absolutely as a leg restraint does - [stated]'s spec for
        // them is "no job possible". Asking FakeStationaryUtil one question rather
        // than enumerating the three here means a fourth would need no change.
        return FakePlayerJobRules.immobilised(FakePlayerRestraintUtil.get(mob))
                || FakeStationaryUtil.isStationary(mob);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        // Re-asserted every tick rather than only on start: a job executor or
        // another mod can hand the navigation a fresh path at any point, and the
        // legs are meant to be absolute.
        mob.getNavigation().stop();

        // Stopping the navigation clears the PATH but not the MoveControl, and
        // Mob#serverAiStep ticks the move control after the goals. A goal
        // interrupted mid-step leaves the control in MOVE_TO with a wanted
        // position still set, so without this the entity keeps drifting the last
        // metre or so towards it after the legs are bound. Pointing the control at
        // the entity's own feet at zero speed ends that in one tick: the distance
        // check inside MoveControl#tick falls under its own threshold and it zeroes
        // the forward input itself.
        mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 0.0d);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
