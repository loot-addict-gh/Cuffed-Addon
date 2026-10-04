package com.example.cuffedaddon.network;

import com.example.cuffedaddon.enchantment.IllusionUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server -> client. Tells every client that renders this player whether their leg
 * restraint is an Illusion, and therefore whether to hold their legs still.
 *
 * <p>This is the only part of Illusion that has to travel. See
 * {@code IllusionUtil}'s client-mirror section for why: legs are animated
 * independently on every client that draws the player, so a lock applied only on
 * the wearer's own machine would fool nobody.
 *
 * <p>Keyed by UUID rather than entity id, unlike this addon's pose packets. Those
 * carry positions and bounding boxes and need the actual {@code Entity} on
 * arrival; this carries one boolean that is consulted later, during rendering, so
 * a stable key that survives the entity being unloaded and reloaded is the better
 * fit and avoids a null-entity race on join.
 */
public class IllusionLegsSyncPacket {

    private final UUID player;
    private final boolean illusion;

    public IllusionLegsSyncPacket(UUID player, boolean illusion) {
        this.player = player;
        this.illusion = illusion;
    }

    public static void encode(IllusionLegsSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.player);
        buf.writeBoolean(packet.illusion);
    }

    public static IllusionLegsSyncPacket decode(FriendlyByteBuf buf) {
        return new IllusionLegsSyncPacket(buf.readUUID(), buf.readBoolean());
    }

    public static void handle(IllusionLegsSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> IllusionUtil.setClientIllusionLegs(packet.player, packet.illusion));
        ctx.get().setPacketHandled(true);
    }
}
