package com.example.cuffedaddon.fakeplayer;

import com.lazrproductions.cuffed.restraints.base.RestraintType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Plain backing implementation of {@link IFakeRestrained} - same shape as
 * {@code CollaredCapability}/{@code PlayerPickedCapability}.
 *
 * <p>Stored as three separate id/flag pairs rather than a map, matching how
 * Cuffed's own capability lays its three slots out, and serialised under the same
 * kind of tag names so a future migration between the two would be readable.
 */
public class FakeRestrainedCapability implements IFakeRestrained {

    @Nullable
    private ResourceLocation headId;
    @Nullable
    private ResourceLocation armsId;
    @Nullable
    private ResourceLocation legsId;

    private boolean headEnchanted;
    private boolean armsEnchanted;
    private boolean legsEnchanted;

    private ItemStack headStack = ItemStack.EMPTY;
    private ItemStack armsStack = ItemStack.EMPTY;
    private ItemStack legsStack = ItemStack.EMPTY;

    @Override
    @Nullable
    public ResourceLocation getRestraintId(RestraintType type) {
        return switch (type) {
            case Head -> headId;
            case Arm -> armsId;
            case Leg -> legsId;
        };
    }

    @Override
    public void setRestraintId(RestraintType type, @Nullable ResourceLocation id) {
        switch (type) {
            case Head -> headId = id;
            case Arm -> armsId = id;
            case Leg -> legsId = id;
        }
    }

    @Override
    public boolean isEnchanted(RestraintType type) {
        return switch (type) {
            case Head -> headEnchanted;
            case Arm -> armsEnchanted;
            case Leg -> legsEnchanted;
        };
    }

    @Override
    public void setEnchanted(RestraintType type, boolean enchanted) {
        switch (type) {
            case Head -> headEnchanted = enchanted;
            case Arm -> armsEnchanted = enchanted;
            case Leg -> legsEnchanted = enchanted;
        }
    }

    @Override
    public ItemStack getStack(RestraintType type) {
        return switch (type) {
            case Head -> headStack;
            case Arm -> armsStack;
            case Leg -> legsStack;
        };
    }

    @Override
    public void setStack(RestraintType type, ItemStack stack) {
        ItemStack kept = stack == null ? ItemStack.EMPTY : stack;
        switch (type) {
            case Head -> headStack = kept;
            case Arm -> armsStack = kept;
            case Leg -> legsStack = kept;
        }
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        putSlot(tag, "Head", headId, headEnchanted, headStack);
        putSlot(tag, "Arm", armsId, armsEnchanted, armsStack);
        putSlot(tag, "Leg", legsId, legsEnchanted, legsStack);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        headId = readId(tag, "Head");
        armsId = readId(tag, "Arm");
        legsId = readId(tag, "Leg");
        headEnchanted = tag.getBoolean("HeadEnchanted");
        armsEnchanted = tag.getBoolean("ArmEnchanted");
        legsEnchanted = tag.getBoolean("LegEnchanted");
        headStack = readStack(tag, "Head");
        armsStack = readStack(tag, "Arm");
        legsStack = readStack(tag, "Leg");
    }

    private static void putSlot(CompoundTag tag, String name, @Nullable ResourceLocation id, boolean enchanted,
                                 ItemStack stack) {
        if (id != null) {
            tag.putString(name, id.toString());
        }
        tag.putBoolean(name + "Enchanted", enchanted);
        if (!stack.isEmpty()) {
            tag.put(name + "Item", stack.save(new CompoundTag()));
        }
    }

    /**
     * The stored stack, or empty. Absent is the normal case for a world saved
     * before the stack was kept, and for anything applied by a command; removal
     * falls back to a fresh item in that case, which is what it always used to do.
     */
    private static ItemStack readStack(CompoundTag tag, String name) {
        String key = name + "Item";
        return tag.contains(key) ? ItemStack.of(tag.getCompound(key)) : ItemStack.EMPTY;
    }

    @Nullable
    private static ResourceLocation readId(CompoundTag tag, String name) {
        // tryParse rather than the throwing constructor: a hand-edited or
        // corrupted tag should leave the slot empty, not break the entity.
        return tag.contains(name) ? ResourceLocation.tryParse(tag.getString(name)) : null;
    }
}
