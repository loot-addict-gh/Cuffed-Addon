package com.example.cuffedaddon.necklace;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;

/**
 * Per-player state for the Key Necklace - the addon's own FIFTH worn slot,
 * sitting alongside Cuffed's three (head/arms/legs) and this addon's own
 * fourth (the Shock Collar's {@code ICollared}).
 *
 * <p><b>Why a slot of its own and not the collar's.</b> [stated] asked for it
 * "in a new slot on its own", and the two genuinely are independent: a player
 * can be collared and wearing a necklace at the same time, and neither ending
 * should disturb the other. Sharing one capability would have meant one of them
 * evicting the other, which is the opposite of what a neck with two things on
 * it should do. The same reasoning that kept the collar out of Cuffed's three
 * slots (see {@code ICollared}, which explains it at length, Curios included)
 * applies unchanged here, so it is not repeated.
 *
 * <p><b>Why this one is NOT a restraint.</b> Nothing about the necklace takes
 * anything away from its wearer - no movement, item-use, mining or jumping
 * restriction, no struggle durability, no breaking. It is a worn container for
 * a Handcuffs Key, and the only thing it does is sit somewhere the wearer
 * cannot reach while their arms are bound. That is why it is not registered as
 * an {@code AbstractRestraint}, never appears in the restraint HUD, and is
 * removed with the empty-handed crouch gesture Cuffed uses for its own
 * NON-keyed restraints rather than with a key.
 *
 * <h2>The whole worn ItemStack is stored, not a boolean</h2>
 * {@code ICollared} keeps a flag plus a binding id because the collar's other
 * half - the remote - is the item and the collar itself is pure state. The
 * necklace has no second half: the thing hanging round the wearer's neck IS the
 * item that was in someone's hand a moment ago, and it goes back into a hand
 * when it comes off. Keeping the real stack means a renamed, enchanted or
 * otherwise NBT-carrying necklace comes back exactly as it went on, instead of
 * being silently replaced by a freshly minted plain one.
 */
public interface INecklaced {

    /** The necklace currently worn, or an empty stack. Never null. */
    @Nonnull
    ItemStack getWorn();

    /** Replaces the worn necklace. Pass {@link ItemStack#EMPTY} to clear the slot. */
    void setWorn(@Nonnull ItemStack stack);

    default boolean isWearing() {
        return !getWorn().isEmpty();
    }

    /**
     * Empties the slot and hands back what was in it, so a caller can never
     * forget one half of the swap and leave the item duplicated or destroyed.
     */
    @Nonnull
    default ItemStack takeWorn() {
        ItemStack worn = getWorn();
        setWorn(ItemStack.EMPTY);
        return worn;
    }

    CompoundTag serializeNBT();

    void deserializeNBT(CompoundTag tag);
}
