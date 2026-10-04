package com.example.cuffedaddon.trap;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * The one block state property shared by every redstone restraint trap.
 *
 * <h2>What "armed" means</h2>
 * {@code armed} is <em>not</em> "has a redstone signal" - the live signal is
 * always read straight from {@code level.hasNeighborSignal(pos)}, which costs
 * nothing to query and can never drift out of sync with the world. {@code armed}
 * is the one bit that genuinely has to be remembered: <b>whether this trap has
 * already fired since its signal last went low.</b>
 *
 * <p>That is exactly [stated]'s rule - "restraining a player should mark them as
 * unpowered even if they still have a powered redstone signal on them, they
 * should require a reset". A trap fires only while
 * {@code hasNeighborSignal && armed}; firing sets {@code armed = false}; and the
 * signal going low sets it back to true. Because both conditions are pure
 * functions of (live signal, armed), there is no rising-edge bookkeeping to get
 * wrong: an unrelated block update next to a spent, still-powered trap can never
 * re-arm it, because the signal is still high.
 *
 * <h2>Why it is a real block state property</h2>
 * So an observer sees it. An observer pulses on a block STATE change at the
 * position it faces, so storing this in a capability or SavedData instead would
 * have made the arm/disarm invisible to redstone. Putting it in the state means
 * a trap firing and a trap resetting are both observable, which is what makes
 * these usable as contraption components rather than just as decorations.
 *
 * <h2>The default value is deliberately not relied upon</h2>
 * For the pillory this property is grafted onto Cuffed's own block by
 * {@code PilloryArmedMixin}, which cannot set that block's registered default
 * state without also injecting into its constructor. Rather than depend on
 * whatever {@code StateDefinition#any()} happens to pick for a fresh boolean,
 * every read goes through {@link #isArmed} and the reset path sets the property
 * to true whenever the signal is low - so a freshly placed trap self-corrects on
 * the first evaluation regardless of what it started as. Do not add a constructor
 * injection to "fix" this; it is not broken.
 */
public final class TrapBlockStates {

    /** Whether this trap will still catch someone before its signal is cycled. */
    public static final BooleanProperty ARMED = BooleanProperty.create("armed");

    private TrapBlockStates() {
    }

    /**
     * Read ARMED defensively. A state that does not carry the property at all
     * (an old world, or the pillory mixin somehow not applying) reads as armed,
     * which degrades to "behaves like it always did" rather than "trap silently
     * never works".
     */
    public static boolean isArmed(BlockState state) {
        return !state.hasProperty(ARMED) || state.getValue(ARMED);
    }
}
