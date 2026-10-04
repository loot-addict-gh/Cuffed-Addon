package com.example.cuffedaddon.fakeplayer;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;

/**
 * Pillory state for a Fake Players fake player - the addon's parallel to Cuffed's
 * own {@code IDetainableEntity}.
 *
 * <h2>Why this could not reuse Cuffed's own detain state</h2>
 * Cuffed's detain state is not a capability at all. It lives in four
 * {@code EntityDataAccessor}s declared in {@code PlayerMixin} as
 * {@code SynchedEntityData.defineId(Player.class, ...)}, and the interface that
 * reads them, {@code IDetainableEntity}, is grafted on by
 * {@code @Mixin(Player.class)}. A fake player is a {@code PathfinderMob}, so:
 * <ul>
 *   <li>it is not a {@code Player}, so it never gets that mixin and never gets
 *       those accessors;</li>
 *   <li>the accessors are keyed to {@code Player.class}, and vanilla's
 *       {@code SynchedEntityData} rejects an accessor registered for a class the
 *       entity is not an instance of - they could not be reused even by hand.</li>
 * </ul>
 * Exactly the same wall {@code IFakeRestrained} ran into, and the same answer: an
 * addon-owned capability holding only what a fake player actually needs.
 *
 * <h2>What a fake player actually needs</h2>
 * Cuffed stores a detain TYPE (an int, {@code 0} = pillory, {@code -1} = free)
 * because the guillotine shares the mechanism. Nothing here does, so this is a
 * plain boolean. The rest is the same three things Cuffed keeps: the exact
 * position to hold, the facing to hold, and the block it is held to - so the
 * pillory can be found again and the entity released when it is broken.
 *
 * <p>Capabilities do not sync, and the whole point of this state is that other
 * players see the pose, so every mutation is broadcast to trackers - see
 * {@code FakeStationaryUtil#syncDetained}.
 */
public interface IFakeDetained {

    boolean isDetained();

    void setDetained(boolean detained);

    double getLockedX();

    double getLockedY();

    double getLockedZ();

    void setLockedPos(double x, double y, double z);

    /**
     * The yaw to hold while pilloried - Cuffed's own
     * {@code PilloryBlock#getFacingRotation}, i.e. the block's FACING as a yaw.
     * Fed straight into Cuffed's {@code animatePilloryDetainedAnimation} as its
     * {@code forwardRotation} argument, exactly as Cuffed feeds its own
     * {@code getDetainedRotation()} there for a real player.
     */
    float getLockedYaw();

    void setLockedYaw(float yaw);

    /**
     * The pillory's UPPER half position - the same block Cuffed records in
     * {@code DATA_DETAINED_TO_BLOCK}, and for the same reason: it is what lets the
     * per-tick check notice the pillory has been broken and let the entity go
     * rather than pinning it to thin air forever.
     */
    @Nullable
    BlockPos getAnchorPos();

    void setAnchorPos(@Nullable BlockPos pos);

    /**
     * The fake player's own pose (STANDING/SITTING/LAYING, as an ordinal - see
     * {@link IFakePlayerPhysicalState}) from just before a stationary restraint went
     * on, so it can be handed back when the restraint comes off.
     *
     * <h2>Why this lives on the PILLORY capability</h2>
     * It is shared by all three stationary restraints, not just the pillory, which
     * makes it look misplaced. The alternative was worse: the Bed and Wall
     * Restraints deliberately reuse {@code ILiePose}/{@code IWallPose}, which are
     * shared with real players, and a real player has no such pose - adding a
     * fake-player-only field to either would put fake-player state into the general
     * pose system this feature was careful to keep out of it. This capability is the
     * only one that is BOTH fake-player-specific and attached to every fake player,
     * so it is the only honest home for it.
     */
    int getSavedPhysicalState();

    void setSavedPhysicalState(int ordinal);

    CompoundTag serializeNBT();

    void deserializeNBT(CompoundTag tag);
}
