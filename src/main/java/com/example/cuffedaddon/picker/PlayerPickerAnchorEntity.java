package com.example.cuffedaddon.picker;

import com.example.cuffedaddon.init.ModEntityTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nonnull;

/**
 * Invisible, size-0, physics-free "carrier" entity that a picked player
 * RIDES (Entity#startRiding) instead of being teleported directly every
 * tick. Rendered with NoopRenderer (see ClientEntityEvents) - purely an
 * attachment point, nothing to look at.
 *
 * WHY THIS EXISTS (replaces the old direct-teleportTo approach in
 * PlayerPickerUtil#tickPickedPlayer): [stated] reported two bugs with that
 * approach even after it was switched to RelativeMovement.ROTATION (meant
 * to leave the picked player's own look alone) - their camera visibly
 * LOCKS while their captor is moving, and pressing the sneak/crouch key
 * snaps their camera to face south. Both point at the same underlying
 * cause: ServerPlayer#teleportTo (any overload, relative-rotation flags or
 * not) always goes through the SAME "authoritative position packet +
 * client teleport-confirmation" pathway a player's own client normally
 * only sees when something forcibly overrides their self-authoritative
 * movement. Sending that packet essentially every tick (since the
 * anchor - a walking player - moves almost every tick) keeps the picked
 * player's client in an almost permanent resync/confirmation cycle, which
 * matches a "locked" feeling camera; the south-facing snap is consistent
 * with the client falling back to treating a queued/stale packet's 0,0
 * rotation fields as ABSOLUTE rather than relative under that condition.
 *
 * The fix vanilla itself already relies on for this exact situation
 * (riding a moving boat/horse/minecart with full free look, no lock, no
 * snapping): a PASSENGER's position sync never goes through the teleport-
 * confirmation pathway at all - it just follows the vehicle's own ordinary
 * tracked-entity movement packets (ClientboundMoveEntityPacket, no ack
 * required), which never touch the passenger's own rotation. So instead of
 * teleporting the picked player, PlayerPickerUtil now keeps ONE of these
 * anchor entities positioned at the resolved anchor point every tick
 * (plain #setPos, not a teleport packet) and has the picked player ride it -
 * vanilla's existing passenger plumbing does the rest, entirely separately
 * from the rider's own look control.
 */
public class PlayerPickerAnchorEntity extends Entity {

    public PlayerPickerAnchorEntity(EntityType<? extends PlayerPickerAnchorEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public PlayerPickerAnchorEntity(Level level, Vec3 pos) {
        this(ModEntityTypes.PLAYER_PICKER_ANCHOR.get(), level);
        this.setPos(pos.x, pos.y, pos.z);
    }

    @Override
    protected void defineSynchedData() {
        // No synced state needed - this entity has no visible/behavioral
        // state of its own, it's purely a moving attachment point.
    }

    @Override
    protected void readAdditionalSaveData(@Nonnull CompoundTag tag) {
        // Not meant to persist across a world save/reload - PlayerPickerUtil
        // recreates one on demand whenever a picked player isn't already
        // riding a valid one (see tickPickedPlayer). Nothing to read.
    }

    @Override
    protected void addAdditionalSaveData(@Nonnull CompoundTag tag) {
        // See readAdditionalSaveData - nothing saved on purpose.
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public double getPassengersRidingOffset() {
        // 0, not vanilla's default (a fraction of this entity's own
        // height) - PlayerPickerUtil already computes the exact target Y
        // the picked player should be at before positioning this entity,
        // no extra offset wanted on top of that.
        return 0.0D;
    }

    @Nonnull
    @Override
    public EntityDimensions getDimensions(@Nonnull Pose pose) {
        return EntityDimensions.scalable(0.01F, 0.01F);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return false;
    }
}
