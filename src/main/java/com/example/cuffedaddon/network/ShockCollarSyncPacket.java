package com.example.cuffedaddon.network;

import com.example.cuffedaddon.capability.ModCapabilities;
import com.example.cuffedaddon.client.ClientCollaredState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client collar state, broadcast to everyone TRACKING the wearer as
 * well as to the wearer themselves.
 *
 * <p><b>Why trackers and not just the wearer.</b> Forge capabilities attached
 * through {@code AttachCapabilitiesEvent} are not synced to clients at all - a
 * remote player's client-side capability always reads back its defaults. Since
 * the worn collar has to be VISIBLE on other players, the render layer needs
 * that state on every client that can see them, so this follows the
 * broadcast-to-trackers pattern {@code LiePoseSyncPacket}/
 * {@code WallPoseSyncPacket} already use for their own render-affecting state
 * rather than the self-only pattern {@code PlayerPickedSelfSyncPacket} uses.
 *
 * <p>The durability numbers ride along on the same packet but are only unpacked
 * into {@code ClientCollaredState} when the subject IS the receiving player -
 * they exist for that player's own HUD bar, and nobody else's client has any
 * use for them.
 */
public class ShockCollarSyncPacket {

    private final int entityId;
    private final boolean collared;
    private final int durability;
    private final int maxDurability;

    public ShockCollarSyncPacket(int entityId, boolean collared, int durability, int maxDurability) {
        this.entityId = entityId;
        this.collared = collared;
        this.durability = durability;
        this.maxDurability = maxDurability;
    }

    public static void encode(ShockCollarSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeBoolean(packet.collared);
        buf.writeVarInt(packet.durability);
        buf.writeVarInt(packet.maxDurability);
    }

    public static ShockCollarSyncPacket decode(FriendlyByteBuf buf) {
        return new ShockCollarSyncPacket(buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(ShockCollarSyncPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) {
                return;
            }
            Entity entity = minecraft.level.getEntity(packet.entityId);
            if (!(entity instanceof Player player)) {
                return;
            }

            // Mirror onto that player's own client-side capability - this is
            // what ShockCollarLayer reads when deciding whether to draw the
            // collar on a player, local or remote.
            player.getCapability(ModCapabilities.COLLARED).ifPresent(cap -> {
                cap.setCollared(packet.collared);
                cap.setDurability(packet.durability);
            });

            if (minecraft.player != null && minecraft.player.getId() == packet.entityId) {
                ClientCollaredState.set(packet.collared, packet.durability, packet.maxDurability);
            }
        });
        ctx.setPacketHandled(true);
    }
}
