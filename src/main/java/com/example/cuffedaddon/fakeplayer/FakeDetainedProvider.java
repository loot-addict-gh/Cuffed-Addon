package com.example.cuffedaddon.fakeplayer;

import com.example.cuffedaddon.capability.ModCapabilities;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Same shape as {@code FakeRestrainedProvider}. Attached only to Fake Players
 * entities - see {@code FakePlayerEvents#onAttachCapabilities}.
 */
public class FakeDetainedProvider implements ICapabilitySerializable<CompoundTag> {

    private final FakeDetainedCapability backing = new FakeDetainedCapability();
    private final LazyOptional<IFakeDetained> optional = LazyOptional.of(() -> backing);

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ModCapabilities.FAKE_DETAINED) {
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
