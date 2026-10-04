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
 * Same shape as {@code CollaredProvider}. Attached only to Fake Players entities
 * - see {@code FakePlayerEvents#onAttachCapabilities}.
 *
 * <p>Being an {@code ICapabilitySerializable}, this persists through world saves
 * for free, which is the whole reason the state lives in a capability rather than
 * in synched entity data: the latter would have needed an injection into their
 * {@code addAdditionalSaveData}/{@code readAdditionalSaveData}, and those are
 * vanilla-derived overrides, which would have meant new hand-maintained refmap
 * entries. Syncing is the trade - capabilities don't sync, so it is done
 * explicitly (see {@code FakePlayerRestraintUtil#sync}).
 */
public class FakeRestrainedProvider implements ICapabilitySerializable<CompoundTag> {

    private final FakeRestrainedCapability backing = new FakeRestrainedCapability();
    private final LazyOptional<IFakeRestrained> optional = LazyOptional.of(() -> backing);

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ModCapabilities.FAKE_RESTRAINED) {
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
