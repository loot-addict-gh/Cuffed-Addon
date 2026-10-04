package com.example.cuffedaddon.network;

import com.example.cuffedaddon.pose.IWallPose;
import com.example.cuffedaddon.pose.WallPoseUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client, mirrors cuffedaddon's LiePoseSyncPacket. Simpler than
 * that one: no bounding-box rebuild on receipt, since the Wall Restraint
 * deliberately never touches the entity's hitbox at all - only position
 * needs the same "moveTo, not setPos" instant-snap treatment (see that
 * packet's own doc for why moveTo specifically, not setPos, avoids remote-
 * entity lerp sliding for observers).
 */
public class WallPoseSyncPacket {

    private final int entityId;
    private final boolean posed;
    private final float lockedYaw;
    private final int lockedSlot;
    private final double lockedX;
    private final double lockedY;
    private final double lockedZ;

    public WallPoseSyncPacket(int entityId, boolean posed, float lockedYaw, int lockedSlot,
                               double lockedX, double lockedY, double lockedZ) {
        this.entityId = entityId;
        this.posed = posed;
        this.lockedYaw = lockedYaw;
        this.lockedSlot = lockedSlot;
        this.lockedX = lockedX;
        this.lockedY = lockedY;
        this.lockedZ = lockedZ;
    }

    public static void encode(WallPoseSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeBoolean(packet.posed);
        buf.writeFloat(packet.lockedYaw);
        buf.writeVarInt(packet.lockedSlot);
        buf.writeDouble(packet.lockedX);
        buf.writeDouble(packet.lockedY);
        buf.writeDouble(packet.lockedZ);
    }

    public static WallPoseSyncPacket decode(FriendlyByteBuf buf) {
        return new WallPoseSyncPacket(buf.readVarInt(), buf.readBoolean(), buf.readFloat(), buf.readVarInt(),
                buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public static void handle(WallPoseSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            Entity entity = mc.level.getEntity(packet.entityId);
            // LivingEntity, not Player - 1.5.11, same widening as LiePoseSyncPacket.
            if (entity instanceof LivingEntity player) {
                IWallPose cap = WallPoseUtil.get(player);
                if (cap != null) {
                    cap.setPosed(packet.posed);
                    cap.setLockedYaw(packet.lockedYaw);
                    cap.setLockedSlot(packet.lockedSlot);
                    cap.setLockedPos(packet.lockedX, packet.lockedY, packet.lockedZ);
                }

                if (packet.posed) {
                    player.moveTo(packet.lockedX, packet.lockedY, packet.lockedZ, packet.lockedYaw, player.getXRot());
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
