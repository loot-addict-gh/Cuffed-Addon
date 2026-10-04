package com.example.cuffedaddon.pose;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;

/**
 * Per-player state for the Wall Restraint pose. Deliberately much simpler
 * than cuffedaddon's ILiePose (Bed Restraint/Lie Pose): the player stays in
 * their normal STANDING pose the whole time (no whole-body render rotation,
 * no custom bounding box) - only position is locked and limb angles are
 * overridden (see mixin.HumanoidModelWallPoseMixin). That's the "no
 * complicated mechanics that alter hitbox shape/size/position" the feature
 * was explicitly asked to avoid.
 *
 * lockedPrimaryPos is the WallRestraintBlock's own PRIMARY-half position -
 * recorded so the block can be found again and flipped back to its
 * unoccupied (red) texture on release/death, the same way ILiePose records
 * getLockedBedPos for the bed it came from.
 */
public interface IWallPose {

    boolean isPosed();

    void setPosed(boolean posed);

    double getLockedX();

    double getLockedY();

    double getLockedZ();

    void setLockedPos(double x, double y, double z);

    /** Fixed yaw to hold while posed - facing straight out from the wall panel. */
    float getLockedYaw();

    void setLockedYaw(float yaw);

    int getLockedSlot();

    void setLockedSlot(int slot);

    @Nullable
    BlockPos getLockedPrimaryPos();

    void setLockedPrimaryPos(@Nullable BlockPos pos);

    CompoundTag serializeNBT();

    void deserializeNBT(CompoundTag tag);
}
