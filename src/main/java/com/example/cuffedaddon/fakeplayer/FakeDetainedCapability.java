package com.example.cuffedaddon.fakeplayer;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;

/**
 * Plain backing implementation of {@link IFakeDetained} - same shape as
 * {@code LiePoseCapability}, deliberately, since the two hold nearly the same
 * kind of state and are read by the same kind of code.
 */
public class FakeDetainedCapability implements IFakeDetained {

    private boolean detained;
    private double lockedX, lockedY, lockedZ;
    private float lockedYaw;
    @Nullable
    private BlockPos anchorPos;
    private int savedPhysicalState = IFakePlayerPhysicalState.STANDING;

    @Override
    public boolean isDetained() {
        return detained;
    }

    @Override
    public void setDetained(boolean detained) {
        this.detained = detained;
    }

    @Override
    public double getLockedX() {
        return lockedX;
    }

    @Override
    public double getLockedY() {
        return lockedY;
    }

    @Override
    public double getLockedZ() {
        return lockedZ;
    }

    @Override
    public void setLockedPos(double x, double y, double z) {
        this.lockedX = x;
        this.lockedY = y;
        this.lockedZ = z;
    }

    @Override
    public float getLockedYaw() {
        return lockedYaw;
    }

    @Override
    public void setLockedYaw(float yaw) {
        this.lockedYaw = yaw;
    }

    @Override
    @Nullable
    public BlockPos getAnchorPos() {
        return anchorPos;
    }

    @Override
    public void setAnchorPos(@Nullable BlockPos pos) {
        this.anchorPos = pos == null ? null : pos.immutable();
    }

    @Override
    public int getSavedPhysicalState() {
        return savedPhysicalState;
    }

    @Override
    public void setSavedPhysicalState(int ordinal) {
        this.savedPhysicalState = ordinal;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Detained", detained);
        tag.putDouble("LockedX", lockedX);
        tag.putDouble("LockedY", lockedY);
        tag.putDouble("LockedZ", lockedZ);
        tag.putFloat("LockedYaw", lockedYaw);
        tag.putInt("SavedPose", savedPhysicalState);
        if (anchorPos != null) {
            tag.putInt("AnchorX", anchorPos.getX());
            tag.putInt("AnchorY", anchorPos.getY());
            tag.putInt("AnchorZ", anchorPos.getZ());
        }
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        this.detained = tag.getBoolean("Detained");
        this.lockedX = tag.getDouble("LockedX");
        this.lockedY = tag.getDouble("LockedY");
        this.lockedZ = tag.getDouble("LockedZ");
        this.lockedYaw = tag.getFloat("LockedYaw");
        // Absent in a world saved before 1.5.11, where getInt returns 0 - which is
        // STANDING, the right thing to hand back when nothing better is known.
        this.savedPhysicalState = tag.getInt("SavedPose");
        this.anchorPos = tag.contains("AnchorX")
                ? new BlockPos(tag.getInt("AnchorX"), tag.getInt("AnchorY"), tag.getInt("AnchorZ"))
                : null;
    }
}
