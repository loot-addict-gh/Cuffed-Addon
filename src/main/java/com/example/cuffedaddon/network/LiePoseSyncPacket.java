package com.example.cuffedaddon.network;

import com.example.cuffedaddon.pose.ILiePose;
import com.example.cuffedaddon.pose.LiePoseUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client. Tells clients (everyone tracking the entity, plus the
 * entity's own client) whether a given entity is lie-posed and, if so, the
 * fixed body yaw, locked hotbar slot, and (see ROUND note below) the locked
 * x/y/z.
 *
 * lockedSlot is what lets the LOCAL posed player's own client correct its
 * own hotbar selection back the SAME client tick a key press changes it
 * (see ClientLiePoseEvents.onClientTick) instead of waiting on a server
 * round-trip - that round-trip delay was visible as the hotbar briefly
 * flickering to the new slot before "immediately" snapping back.
 *
 * Also applies/restores the actual collision box locally on receipt - each
 * client maintains its own copy of a tracked entity's bounding box (it's
 * derived locally, not a synced field), so every OTHER player's client needs
 * to shrink it too, not just the posed player's own client/the server. See
 * ClientLiePoseEvents for the recurring per-tick re-assertion that keeps
 * this correct between sync packets (entity position interpolation on the
 * client can otherwise silently recompute the box back to normal).
 *
 * Sent at apply/clear time (LiePoseUtil.setPosed) AND again whenever a
 * client starts tracking an already-posed player, or that player logs back
 * in still posed (see LiePoseEvents) - otherwise a late-joining viewer's own
 * mirrored capability would silently stay at its default false.
 *
 * ROUND (this session): [stated] reported that applying the Bed Restraint
 * to ANOTHER player lays them down starting from wherever they were
 * standing, rather than snapping cleanly to the bed like self-application
 * does - self always looked right regardless of standing position. Root
 * cause: position/rotation used to be left entirely to "normal entity
 * tracking" (see the removed line above this note) - fine for the posed
 * player's OWN client (a local-player position correction from the server
 * is applied instantly, no smoothing), but every OTHER client watching that
 * player is a REMOTE entity to them, and vanilla smooths remote-entity
 * position jumps over several ticks (Entity#lerpTo) - since this addon's
 * render mixin applies the lie-flat rotation immediately and
 * unconditionally (not smoothed at all), onlookers briefly saw an
 * already-flat body sliding from the old standing spot to the bed instead
 * of appearing there directly. Fixed by carrying the locked x/y/z in this
 * packet and calling Entity#moveTo on receipt (see handle below) - moveTo
 * sets BOTH the current and the "old" (lerp origin) position fields, so
 * there's nothing left for the client to interpolate away from - every
 * observer now sees the same instant snap the posed player's own client
 * already did.
 */
public class LiePoseSyncPacket {

    private final int entityId;
    private final boolean posed;
    private final float lockedYaw;
    private final int lockedSlot;
    private final double lockedX;
    private final double lockedY;
    private final double lockedZ;

    public LiePoseSyncPacket(int entityId, boolean posed, float lockedYaw, int lockedSlot,
                              double lockedX, double lockedY, double lockedZ) {
        this.entityId = entityId;
        this.posed = posed;
        this.lockedYaw = lockedYaw;
        this.lockedSlot = lockedSlot;
        this.lockedX = lockedX;
        this.lockedY = lockedY;
        this.lockedZ = lockedZ;
    }

    public static void encode(LiePoseSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeBoolean(packet.posed);
        buf.writeFloat(packet.lockedYaw);
        buf.writeVarInt(packet.lockedSlot);
        buf.writeDouble(packet.lockedX);
        buf.writeDouble(packet.lockedY);
        buf.writeDouble(packet.lockedZ);
    }

    public static LiePoseSyncPacket decode(FriendlyByteBuf buf) {
        return new LiePoseSyncPacket(buf.readVarInt(), buf.readBoolean(), buf.readFloat(), buf.readVarInt(),
                buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public static void handle(LiePoseSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            Entity entity = mc.level.getEntity(packet.entityId);
            // LivingEntity, not Player - 1.5.11: this packet now also carries a
            // bed-restrained Fake Players fake player's pose. Nothing in the body
            // was Player-specific; moveTo/setBoundingBox are Entity API.
            if (entity instanceof LivingEntity player) {
                ILiePose cap = LiePoseUtil.get(player);
                if (cap != null) {
                    cap.setPosed(packet.posed);
                    cap.setLockedYaw(packet.lockedYaw);
                    cap.setLockedSlot(packet.lockedSlot);
                    cap.setLockedPos(packet.lockedX, packet.lockedY, packet.lockedZ);
                }

                if (packet.posed) {
                    // moveTo (not setPos) deliberately - it also resets the
                    // entity's xo/yo/zo "old position" fields, which is
                    // what actually clears any pending remote-entity lerp
                    // target. setPos alone leaves those stale, so a
                    // previously-queued interpolation could still animate
                    // toward the OLD standing spot for a frame or two even
                    // after this runs. See this class's own ROUND note
                    // above for the full reasoning.
                    player.moveTo(packet.lockedX, packet.lockedY, packet.lockedZ, packet.lockedYaw, player.getXRot());
                    player.setBoundingBox(LiePoseUtil.buildLieBoundingBox(
                            packet.lockedX, packet.lockedY, packet.lockedZ, packet.lockedYaw));
                } else {
                    LiePoseUtil.restoreNormalBoundingBox(player);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
