package com.example.cuffedaddon.items;

/**
 * Marks an item as ammunition only the {@link ReinforcedBowItem} will load.
 *
 * <p>Exists so the bow's ammo predicate stays one expression no matter how many
 * special arrows the mod grows. Both arrows are `ArrowItem` subclasses for the
 * reason given in {@link ArrowOfRestraintItem} - vanilla's own
 * {@code BowItem#releaseUsing} dispatches through
 * {@code ArrowItem#createArrow}, which is what lets the bow inherit every rule
 * about firing rather than reimplement it - but neither is in the
 * {@code minecraft:arrows} tag, so no other bow can load them.
 */
public interface ReinforcedBowAmmo {
}
