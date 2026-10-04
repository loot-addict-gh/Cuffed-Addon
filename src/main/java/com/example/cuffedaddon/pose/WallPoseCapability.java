package com.example.cuffedaddon.pose;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;

public class WallPoseCapability implements IWallPose {

    private boolean posed = false;
    private double lockedX, lockedY, lockedZ;
    private float lockedYaw;
    private int lockedSlot;
    @Nullable
    private BlockPos lockedPrimaryPos;

    @Override
    public boolean isPosed() {
        return posed;
    }

    @Override
    public void setPosed(boolean posed) {
        this.posed = posed;
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
    public int getLockedSlot() {
        return lockedSlot;
    }

    @Override
    public void setLockedSlot(int slot) {
        this.lockedSlot = slot;
    }

    @Override
    @Nullable
    public BlockPos getLockedPrimaryPos() {
        return lockedPrimaryPos;
    }

    @Override
    public void setLockedPrimaryPos(@Nullable BlockPos pos) {
        this.lockedPrimaryPos = pos;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Posed", posed);
        tag.putDouble("LockedX", lockedX);
        tag.putDouble("LockedY", lockedY);
        tag.putDouble("LockedZ", lockedZ);
        tag.putFloat("LockedYaw", lockedYaw);
        tag.putInt("LockedSlot", lockedSlot);
        if (lockedPrimaryPos != null) {
            tag.putInt("LockedBlockX", lockedPrimaryPos.getX());
            tag.putInt("LockedBlockY", lockedPrimaryPos.getY());
            tag.putInt("LockedBlockZ", lockedPrimaryPos.getZ());
        }
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        this.posed = tag.getBoolean("Posed");
        this.lockedX = tag.getDouble("LockedX");
        this.lockedY = tag.getDouble("LockedY");
        this.lockedZ = tag.getDouble("LockedZ");
        this.lockedYaw = tag.getFloat("LockedYaw");
        this.lockedSlot = tag.getInt("LockedSlot");
        this.lockedPrimaryPos = tag.contains("LockedBlockX")
                ? new BlockPos(tag.getInt("LockedBlockX"), tag.getInt("LockedBlockY"), tag.getInt("LockedBlockZ"))
                : null;
    }
}
