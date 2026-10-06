package com.example.cuffedaddon.necklace;

import com.example.cuffedaddon.capability.ModCapabilities;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class NecklacedProvider implements ICapabilitySerializable<CompoundTag> {

    private final NecklacedCapability backing = new NecklacedCapability();
    private final LazyOptional<INecklaced> optional = LazyOptional.of(() -> backing);

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ModCapabilities.NECKLACED) {
            return optional.cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        return backing.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        backing.deserializeNBT(tag);
    }
}
