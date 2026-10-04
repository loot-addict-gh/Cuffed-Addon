package com.example.cuffedaddon.fakeplayer;

import com.lazrproductions.cuffed.restraints.base.RestraintType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Restraint state for a Fake Players fake player - the addon's own parallel to
 * Cuffed's {@code IRestrainableCapability}, because none of Cuffed's can be used
 * on one.
 *
 * <h2>Why a parallel capability was unavoidable</h2>
 * {@code FakePlayerEntity extends PathfinderMob}. It is a MOB wearing a player
 * skin, and Cuffed is hardcoded to {@code Player} at every level:
 * {@code ModServerEvents#attachCapability} only attaches on
 * {@code instanceof Player}; the API signature is literally
 * {@code getRestrainableCapability(Player)}; {@code IRestrainableEntity} is
 * grafted on by {@code @Mixin(Player.class)}; and
 * {@code AbstractRestraint} is {@code ServerPlayer}-typed throughout - its
 * constructor, {@code onTickServer}, {@code onEquippedServer} and
 * {@code onUnequippedServer} all take a {@code ServerPlayer}. A mixin cannot
 * widen a parameter type, so there is no route to making Cuffed accept a mob.
 *
 * <h2>Why that turned out to be cheap</h2>
 * A fake player never needs an {@code AbstractRestraint} INSTANCE at all. It
 * cannot struggle, has no durability, is never escorted, and [stated] specified
 * that its head restraints are purely visual. The only state a fake player's
 * restraint has is <b>which restraint is in which slot</b> - an id plus an
 * enchanted flag - which is exactly what {@code IRestrainableEntity} exposes and
 * exactly what every render layer, Cuffed's included, reads. So this capability
 * is deliberately only six values, and {@code FakePlayerEntityMixin} republishes
 * them as {@code IRestrainableEntity} so the existing rendering works untouched.
 *
 * <p>Capabilities are NOT synced to clients, and the whole point of this state is
 * that other players can see it, so every mutation goes out to trackers - see
 * {@code FakePlayerRestraintUtil#sync}.
 */
public interface IFakeRestrained {

    @Nullable
    ResourceLocation getRestraintId(RestraintType type);

    void setRestraintId(RestraintType type, @Nullable ResourceLocation id);

    boolean isEnchanted(RestraintType type);

    void setEnchanted(RestraintType type, boolean enchanted);

    /**
     * The exact item that was put on this slot, or an empty stack if unknown.
     *
     * <h2>Why the id alone was not enough</h2>
     * Removal used to hand back {@code new ItemStack(restraint.getItem())}, which
     * is a brand new item: full durability, no enchantments, no custom name. So
     * unlocking a nearly-broken pair of handcuffs off a fake player <b>repaired
     * them</b>, and unlocking an enchanted restraint silently destroyed the
     * enchantments. Cuffed does not do that to a real player - its
     * {@code UnequipRestraint} goes through {@code AbstractRestraint#saveToItemStack},
     * which restores the stored item data, the damage value and the enchantments.
     *
     * <p>Keeping the stack itself is the fake-player equivalent, and it is simpler
     * than Cuffed's: with no durability ticking and no struggling, the item cannot
     * change while it is worn, so what went on is exactly what should come off.
     * The {@code enchanted} flag is kept alongside it only because
     * {@code IRestrainableEntity} exposes it for rendering, and because a world
     * saved before this existed has the flag but no stack.
     */
    ItemStack getStack(RestraintType type);

    void setStack(RestraintType type, ItemStack stack);

    default boolean has(RestraintType type) {
        return getRestraintId(type) != null;
    }

    /** True if anything at all is equipped, in any of the three slots. */
    default boolean isRestrained() {
        return has(RestraintType.Head) || has(RestraintType.Arm) || has(RestraintType.Leg);
    }

    CompoundTag serializeNBT();

    void deserializeNBT(CompoundTag tag);
}
