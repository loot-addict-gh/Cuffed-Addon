package com.example.cuffedaddon.pose;

import com.example.cuffedaddon.blocks.WallRestraintBlock;
import com.example.cuffedaddon.capability.ModCapabilities;
import com.example.cuffedaddon.network.NetworkHandler;
import com.example.cuffedaddon.network.WallPoseSyncPacket;
import com.lazrproductions.cuffed.api.CuffedAPI;
import com.lazrproductions.cuffed.cap.base.IRestrainableCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.List;

public class WallPoseUtil {

    // LivingEntity, not Player - 1.5.11. A wall-restrained Fake Players fake
    // player reuses this whole feature (capability, sync packet, render mixin,
    // cosmetic cuffs layer) rather than getting a parallel one; see
    // fakeplayer/FakeStationaryUtil for the reasoning. Every existing caller
    // already passed something narrower than LivingEntity, so widening the READ
    // side breaks nothing. The WRITE side below stays ServerPlayer-typed - a fake
    // player has no connection to teleport through and no hotbar to lock.
    @Nullable
    public static IWallPose get(net.minecraft.world.entity.LivingEntity entity) {
        return entity.getCapability(ModCapabilities.WALL_POSE).orElse(null);
    }

    /** Same cheap-gate-first reasoning as {@code LiePoseUtil#isPosed} - see its doc. */
    public static boolean isPosed(net.minecraft.world.entity.LivingEntity entity) {
        if (!(entity instanceof Player)
                && !com.example.cuffedaddon.fakeplayer.FakePlayerSupport.isFakePlayer(entity)) {
            return false;
        }
        IWallPose cap = get(entity);
        return cap != null && cap.isPosed();
    }

    /**
     * Finds a player whose feet are at the panel's own base (the LOWER
     * cell's X/Z/Y) - "when a player stands on the block area itself". Only
     * the lower cell is checked - a standing player's feet can't physically
     * be at the upper cell's height while their feet are still on the
     * ground, same as how a real door is normally interacted with regardless
     * of which half you click. Deliberately a plain column match, not a
     * physical overlap test against the thin door-shaped collision box -
     * the panel's shape only occupies 3/16 of the cell, so a player can
     * physically stand in the same cell as the panel already (same as
     * standing right up against a closed door), and this is simpler/more
     * predictable than re-deriving that from the shape.
     */
    @Nullable
    public static ServerPlayer findStandingPlayer(Level level, BlockPos lowerPos) {
        AABB searchBox = new AABB(lowerPos.getX(), lowerPos.getY(), lowerPos.getZ(),
                lowerPos.getX() + 1.0, lowerPos.getY() + 2.0, lowerPos.getZ() + 1.0);
        List<ServerPlayer> candidates = level.getEntitiesOfClass(ServerPlayer.class, searchBox);
        for (ServerPlayer candidate : candidates) {
            if (candidate.blockPosition().equals(lowerPos)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Applies the pose: locks the target's current position and sets their
     * facing yaw to look straight out from the panel (away from it, along
     * FACING). Refuses (returns false, no message - same silent style as
     * Bed Restraint's own preconditions) if already posed (either kind) or
     * already wearing an arm/leg restraint, matching Bed Restraint's own
     * precondition exactly.
     */
    /**
     * How far to pull the locked position toward the panel from the cell's
     * exact center (in blocks) - "a bit further towards the block itself so
     * it looks like the player is actually glued to the wall". The panel's
     * own thin slab sits flush against the far edge of the cell (see
     * WallRestraintBlock's SHAPE_* constants, e.g. z 0.8125-1.0 for
     * FACING=NORTH) - moving the locked position from the 0.5 cell-center
     * toward that edge by this much (without touching the player's actual
     * hitbox/collision at all, per the request) reads as standing right up
     * against it rather than out in the middle of the cell.
     */
    private static final double TOWARD_WALL_OFFSET = 0.15;

    /**
     * Where a wall-restrained entity's feet are locked, for a given panel. Split out
     * in 1.5.11 so the Fake Players path (fakeplayer/FakeStationaryUtil#tryWall)
     * shares this one definition of "glued to the wall" rather than copying the
     * offset - see TOWARD_WALL_OFFSET's own doc for what that offset is for.
     */
    public static Vec3 wallLockedPos(BlockPos primaryPos, Direction facing) {
        double lockedX = primaryPos.getX() + 0.5 - facing.getStepX() * TOWARD_WALL_OFFSET;
        double lockedZ = primaryPos.getZ() + 0.5 - facing.getStepZ() * TOWARD_WALL_OFFSET;
        return new Vec3(lockedX, primaryPos.getY(), lockedZ);
    }

    public static boolean tryApply(ServerPlayer target, BlockPos primaryPos, Direction facing) {
        IWallPose cap = get(target);
        if (cap == null || cap.isPosed()) {
            return false;
        }

        IRestrainableCapability restrainable = CuffedAPI.Capabilities.getRestrainableCapability(target);
        if (restrainable.armsRestrained() || restrainable.legsRestrained()) {
            return false;
        }

        float yaw = facing.toYRot();

        // Always the panel's own cell center, not wherever the target
        // happened to be standing when restrained - and pulled slightly
        // toward the panel (opposite FACING, since FACING is the outward-
        // facing direction the panel's OPEN side faces) so they read as
        // pinned against it rather than floating in the middle of the cell.
        Vec3 locked = wallLockedPos(primaryPos, facing);
        double lockedX = locked.x;
        double lockedY = locked.y;
        double lockedZ = locked.z;

        cap.setPosed(true);
        cap.setLockedPos(lockedX, lockedY, lockedZ);
        cap.setLockedYaw(yaw);
        cap.setLockedSlot(target.getInventory().selected);
        cap.setLockedPrimaryPos(primaryPos.immutable());

        target.setDeltaMovement(Vec3.ZERO);
        target.setNoGravity(true);
        target.setYRot(yaw);
        target.setYHeadRot(yaw);
        target.setYBodyRot(yaw);
        // Snap immediately rather than waiting for onPlayerTick's drift
        // correction to catch up a tick later - avoids a visible one-tick
        // "slide into place" on application.
        target.connection.teleport(lockedX, lockedY, lockedZ, yaw, target.getXRot());

        NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target),
                new WallPoseSyncPacket(target.getId(), true, yaw, cap.getLockedSlot(),
                        lockedX, lockedY, lockedZ));
        return true;
    }

    /** Clears the pose and restores the block to its unoccupied (red) state. Used by the key-release path. */
    public static boolean release(ServerPlayer target) {
        IWallPose cap = get(target);
        if (cap == null || !cap.isPosed()) {
            return false;
        }

        unoccupyBlock(target.level(), cap.getLockedPrimaryPos());

        cap.setPosed(false);
        cap.setLockedPrimaryPos(null);
        target.setNoGravity(false);

        NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target),
                new WallPoseSyncPacket(target.getId(), false, cap.getLockedYaw(), cap.getLockedSlot(),
                        target.getX(), target.getY(), target.getZ()));
        return true;
    }

    /** Sets a wall restraint block pair back to OCCUPIED=false, if it's still there. */
    public static void unoccupyBlock(Level level, @Nullable BlockPos primaryPos) {
        if (primaryPos == null || level == null) return;
        BlockState primaryState = level.getBlockState(primaryPos);
        if (!(primaryState.getBlock() instanceof WallRestraintBlock)) return;

        BlockPos secondaryPos = WallRestraintBlock.getOtherHalfPos(primaryState, primaryPos);
        level.setBlock(primaryPos, primaryState.setValue(WallRestraintBlock.OCCUPIED, false), 3);
        BlockState secondaryState = level.getBlockState(secondaryPos);
        if (secondaryState.getBlock() instanceof WallRestraintBlock) {
            level.setBlock(secondaryPos, secondaryState.setValue(WallRestraintBlock.OCCUPIED, false), 3);
        }
    }

    /** Finds and releases whoever is currently locked to the panel at primaryPos (used when the block is broken). */
    public static void releaseWhoeverIsAt(Level level, BlockPos primaryPos) {
        for (Player player : level.players()) {
            if (!(player instanceof ServerPlayer serverPlayer)) continue;
            IWallPose cap = get(serverPlayer);
            if (cap != null && cap.isPosed() && primaryPos.equals(cap.getLockedPrimaryPos())) {
                release(serverPlayer);
                return;
            }
        }
    }
}
