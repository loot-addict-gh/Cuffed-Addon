package com.example.cuffedaddon.necklace;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;

/**
 * Plain backing implementation of {@link INecklaced} - same shape as
 * {@code CollaredCapability}/{@code PlayerPickedCapability}.
 */
public class NecklacedCapability implements INecklaced {

    private static final String TAG_WORN = "Worn";

    @Nonnull
    private ItemStack worn = ItemStack.EMPTY;

    @Nonnull
    @Override
    public ItemStack getWorn() {
        return worn;
    }

    @Override
    public void setWorn(@Nonnull ItemStack stack) {
        this.worn = stack;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        if (!worn.isEmpty()) {
            tag.put(TAG_WORN, worn.save(new CompoundTag()));
        }
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        // ItemStack.of returns EMPTY for an absent/invalid compound, so the
        // no-necklace case needs no special handling.
        //
        // The .copy() is not optional-by-taste. ItemStack.of does NOT copy the
        // NBT it reads - vanilla 1.20.1 is literally
        // `this.tag = tag.getCompound("tag")` - so the stack would SHARE the
        // compound it was built from, and `verifyTagAfterLoad` can mutate it
        // even when nothing else does. Sharing happens to be harmless on this
        // path (the source tag is this capability's own, and Forge discards the
        // one it loads from), but the project's standing rule after the 1.5.16
        // Restraint Gaze bug is to copy first and not have to reason about it
        // per call site. ArrowOfRestraintItem and ArrowOfRestraintEntity do the
        // same.
        this.worn = tag.contains(TAG_WORN)
                ? ItemStack.of(tag.getCompound(TAG_WORN).copy())
                : ItemStack.EMPTY;
    }
}
