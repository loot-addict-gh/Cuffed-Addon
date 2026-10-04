package com.example.cuffedaddon.trap;

import java.util.Random;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.example.cuffedaddon.blocks.WallRestraintBlock;
import com.example.cuffedaddon.config.CuffedAddonServerConfig;
import com.example.cuffedaddon.fakeplayer.FakePlayerSupport;
import com.example.cuffedaddon.fakeplayer.FakeStationaryUtil;
import com.lazrproductions.cuffed.blocks.PilloryBlock;
import com.lazrproductions.cuffed.init.ModSounds;

/**
 * The redstone half of the trap set: a powered pillory or Wall Restraint locks
 * up whoever is standing in it.
 *
 * <h2>The state machine, in full</h2>
 * Two inputs, one remembered bit. The live redstone signal is read straight from
 * the world every time ({@code hasNeighborSignal} on EITHER half, so it does not
 * matter which block of a two-tall trap the wire touches), and
 * {@code TrapBlockStates.ARMED} is the only thing persisted:
 * <pre>
 *   no signal            -&gt; re-arm (armed = true) and do nothing else
 *   signal + armed       -&gt; try to catch; if caught, armed = false
 *   signal + not armed   -&gt; do nothing at all, this trap is spent
 * </pre>
 * That last line is [stated]'s requirement: "restraining a player should mark
 * them as unpowered even if they still have a powered redstone signal on them,
 * they should require a reset. this is so that someone can still free a player
 * from the traps and they dont get trapped infinitely." Without it, freeing
 * someone from a trap still sitting on a live signal would re-catch them on the
 * next evaluation, forever.
 *
 * <p>Because both transitions are pure functions of (live signal, armed), there
 * is no rising-edge state to keep: an unrelated block update beside a spent,
 * still-powered trap cannot re-arm it, because the signal is still high. This is
 * the reason there is no second "was powered last tick" property.
 *
 * <h2>Why the check is continuous rather than edge-triggered</h2>
 * [stated]: "the check should be continuous: a powered pillory / wall restraint
 * should check for a player no matter how long theyve been powered on". So the
 * catch attempt runs from two places - the moment the redstone around a trap
 * changes ({@link RedstoneTrapEvents#onNeighborNotify}, which covers "already
 * standing there when the lever is flipped"), and a slow poll around every
 * player ({@link RedstoneTrapEvents#onServerTick}, which covers "walks into an
 * already-powered trap").
 *
 * <h2>Catching reuses each block's own existing path</h2>
 * Nothing here reimplements restraining. The pillory goes through Cuffed's own
 * public {@code attemptToToggleDetained}, which is the same single method this
 * addon's {@code PilloryBlockFakePlayerMixin} already extends, so a redstone
 * catch and a right-click catch behave identically - fake players included, for
 * free. The Wall Restraint goes through {@code WallRestraintBlock#attemptCatch},
 * extracted from that block's own {@code use} for exactly this reason.
 */
public final class RedstoneTrapUtil {

    /** Cuffed's own tolerance for "standing in the pillory", from PilloryBlock. */
    private static final double PILLORY_OCCUPANT_RADIUS = 0.3D;

    private static final Random RANDOM = new Random();

    private RedstoneTrapUtil() {
    }

    public static boolean isTrapBlock(BlockState state) {
        return state.getBlock() instanceof PilloryBlock || state.getBlock() instanceof WallRestraintBlock;
    }

    /**
     * Run the state machine for the trap that {@code pos} belongs to.
     *
     * <p>Safe to call with either half of a two-tall trap, and safe to call
     * repeatedly - it is idempotent whenever nothing has changed.
     */
    public static void evaluate(Level level, BlockPos pos) {
        if (level.isClientSide() || !CuffedAddonServerConfig.TRAP_REDSTONE_ENABLED.get()) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof PilloryBlock) {
            evaluatePillory(level, normalisePillory(level, state, pos));
        } else if (state.getBlock() instanceof WallRestraintBlock) {
            evaluateWall(level, WallRestraintBlock.getPrimaryPos(state, pos));
        }
    }

    /**
     * The pillory acts from its UPPER half - that is where Cuffed's own
     * {@code use} forwards a click to, and the only half whose
     * {@code attemptToToggleDetained} does anything.
     */
    private static BlockPos normalisePillory(Level level, BlockState state, BlockPos pos) {
        return state.getValue(PilloryBlock.HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos;
    }

    private static void evaluatePillory(Level level, BlockPos upperPos) {
        BlockState upperState = level.getBlockState(upperPos);
        if (!(upperState.getBlock() instanceof PilloryBlock pillory)
                || upperState.getValue(PilloryBlock.HALF) != DoubleBlockHalf.UPPER) {
            return;
        }
        BlockPos lowerPos = upperPos.below();

        if (!powered(level, lowerPos, upperPos)) {
            rearm(level, upperPos, lowerPos);
            return;
        }
        if (!TrapBlockStates.isArmed(upperState) || pillory.getClosed(upperState)) {
            return;
        }
        if (!someoneStandingInPillory(level, upperState, upperPos, pillory)) {
            return;
        }

        // Cuffed's toggle returns whether the block should end up OPEN. Passing
        // wasOpen = true puts it on its "try to detain" branch; false back means
        // it actually locked someone in. The standing check above is not
        // redundant - with nobody there this method falls through to
        // "return !wasOpen", i.e. false, which would otherwise read as a
        // successful catch and slam an empty pillory shut.
        if (pillory.attemptToToggleDetained(level, true, upperState, upperPos)) {
            return;
        }

        level.playSound(null, upperPos, ModSounds.PILLORY_USE, SoundSource.BLOCKS, 1.0F,
                0.8F + (RANDOM.nextFloat() * 0.1F));
        // setClosed writes the UPPER half; the LOWER half copies CLOSED from it
        // through PilloryBlock's own updateShape, same as a manual click.
        pillory.setClosed(level, upperPos, level.getBlockState(upperPos), true);
        disarm(level, upperPos, lowerPos);
    }

    private static void evaluateWall(Level level, BlockPos lowerPos) {
        BlockState lowerState = level.getBlockState(lowerPos);
        if (!(lowerState.getBlock() instanceof WallRestraintBlock)) {
            return;
        }
        BlockPos upperPos = lowerPos.above();

        if (!powered(level, lowerPos, upperPos)) {
            rearm(level, lowerPos, upperPos);
            return;
        }
        if (!TrapBlockStates.isArmed(lowerState) || lowerState.getValue(WallRestraintBlock.OCCUPIED)) {
            return;
        }
        if (WallRestraintBlock.attemptCatch(level, lowerPos, lowerState)) {
            disarm(level, lowerPos, upperPos);
        }
    }

    /** A signal reaching EITHER half powers the whole trap. */
    private static boolean powered(Level level, BlockPos a, BlockPos b) {
        return level.hasNeighborSignal(a) || level.hasNeighborSignal(b);
    }

    private static void rearm(Level level, BlockPos... positions) {
        setArmed(level, true, positions);
    }

    private static void disarm(Level level, BlockPos... positions) {
        setArmed(level, false, positions);
    }

    /**
     * Write ARMED to both halves, so an observer works at either height.
     *
     * <p>Flag 3 (UPDATE_NEIGHBORS | UPDATE_CLIENTS) is what makes the observer
     * fire at all: it is what drives vanilla's neighbour-shape pass, and
     * {@code ObserverBlock} watches for its state change from inside
     * {@code updateShape}. Writing with a quieter flag would keep the trap
     * working and silently make it undetectable, which is the whole point of
     * putting this in the block state.
     */
    private static void setArmed(Level level, boolean value, BlockPos... positions) {
        for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);
            if (state.hasProperty(TrapBlockStates.ARMED) && state.getValue(TrapBlockStates.ARMED) != value) {
                level.setBlock(pos, state.setValue(TrapBlockStates.ARMED, value), Block.UPDATE_ALL);
            }
        }
    }

    /**
     * Whether anyone Cuffed would consider an occupant is standing at the
     * pillory's locking spot.
     *
     * <p>Mirrors {@code PilloryBlock#attemptToToggleDetained}'s own lookup - the
     * point behind the block, and a horizontal distance under 0.3 - rather than
     * asking for the nearest player in the world, so the answer agrees with what
     * Cuffed is about to do. The fake-player branch is the same helper this
     * addon's pillory mixin already uses, guarded because Fake Players is an
     * optional dependency and merely resolving its types without it installed
     * throws NoClassDefFoundError.
     */
    private static boolean someoneStandingInPillory(Level level, BlockState upperState, BlockPos upperPos,
                                                     PilloryBlock pillory) {
        Vec3 behind = PilloryBlock.getPositionBehind(upperState, upperPos);
        Player player = realPlayerAt(level, behind);
        if (player != null) {
            return pillory.canDetainPlayer(level, upperState, upperPos, player, true);
        }
        if (!FakePlayerSupport.isModLoaded()) {
            return false;
        }
        LivingEntity fake = FakeStationaryUtil.findFakePlayerBehindPillory(level, behind);
        return fake != null;
    }

    @Nullable
    private static Player realPlayerAt(Level level, Vec3 behind) {
        AABB box = new AABB(behind.x - 1.5, behind.y - 1.5, behind.z - 1.5,
                behind.x + 1.5, behind.y + 1.5, behind.z + 1.5);
        for (Player candidate : level.getEntitiesOfClass(Player.class, box)) {
            if (candidate.isSpectator() || !candidate.isAlive()) {
                continue;
            }
            double dx = behind.x - candidate.position().x;
            double dz = behind.z - candidate.position().z;
            if (Math.sqrt(dx * dx + dz * dz) < PILLORY_OCCUPANT_RADIUS) {
                return candidate;
            }
        }
        return null;
    }
}
