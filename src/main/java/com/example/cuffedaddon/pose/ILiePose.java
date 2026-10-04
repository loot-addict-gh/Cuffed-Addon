package com.example.cuffedaddon.pose;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;

/**
 * Per-player state for the new, generic "lie flat on your back" pose - the
 * from-scratch foundation the 1.2.x rebuild starts with, deliberately built
 * BEFORE any item/restraint hooks into it (see /test command in ModCommands).
 *
 * Unlike the old Bed Restraint's IBedRestraint, there is no bed/anchor block
 * involved at all here: the locked position is simply wherever the player
 * was standing when the pose was applied, captured once, with no offset
 * math layered on top of IT. That removes the entire class of "locked
 * coordinates computed from bed geometry are subtly wrong" bugs the old
 * feature got stuck on - see /areas/cuffedaddon.md's "Bed Restraint (1.2.x)"
 * section for the full history of why this is deliberately being tried in
 * this order this time.
 *
 * NOTE: the locked position is the player's FEET (that's what entity
 * position always is), which is also the pivot point the lie-flat render
 * rotation spins the whole model around - so consumers that need to know
 * where the HEAD ends up after that rotation (camera, hitbox coverage) do
 * still need their own offset math using getLockedYaw(), same idea the old
 * Bed Restraint line used (see LiePoseUtil.buildLieBoundingBox and
 * CameraLiePoseMixin). That's offsetting FROM this locked position for a
 * downstream consumer's own purposes, not changing what gets locked here.
 */
public interface ILiePose {

    boolean isPosed();

    void setPosed(boolean posed);

    double getLockedX();

    double getLockedY();

    double getLockedZ();

    void setLockedPos(double x, double y, double z);

    /** Fixed body yaw to hold while posed, captured from the player's own yRot at the moment the pose was applied. */
    float getLockedYaw();

    void setLockedYaw(float yaw);

    /** Hotbar slot to hold locked while posed, captured at apply time. */
    int getLockedSlot();

    void setLockedSlot(int slot);

    /**
     * ROUND 20: the bed's own FOOT block position, if this pose came from
     * standing on a bed (BedRestraintItem/LiePoseUtil.setPosedOnBed) - null
     * otherwise (e.g. the currently-unused direct setPosed(player, true)
     * path). Lets a listener identify EXACTLY which bed a posed player is
     * occupying, rather than a radius guess - see LiePoseEvents'
     * onRightClickBed for why that distinction matters (two beds pushed
     * right next to each other need to stay independently usable).
     */
    @Nullable
    BlockPos getLockedBedPos();

    void setLockedBedPos(@Nullable BlockPos pos);

    CompoundTag serializeNBT();

    void deserializeNBT(CompoundTag tag);
}
