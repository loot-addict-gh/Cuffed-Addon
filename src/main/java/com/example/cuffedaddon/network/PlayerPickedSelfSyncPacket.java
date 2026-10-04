package com.example.cuffedaddon.network;

import com.example.cuffedaddon.client.ClientPickedState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client, sent ONLY to the affected player themselves (via
 * PacketDistributor.PLAYER, same pattern already used by LiePoseEvents'
 * viewer-only sends) whenever their OWN
 * "picked" state changes - NOT the broadcast-to-trackers pattern
 * WallPoseSyncPacket/LiePoseSyncPacket use, since this isn't about how
 * other players render this player, it's about suppressing the PICKED
 * player's OWN local crouch-key input.
 *
 * [stated] confirmed the earlier server-side-only crouch fix (resetting
 * isShiftKeyDown + forcing Pose.STANDING every tick in tickPickedPlayer)
 * still didn't work - "I can still crouch when im picked up". Root cause:
 * the crouch camera/eye-height blend is CLIENT-PREDICTED from the LOCAL
 * player's own Minecraft.options.keySneak state, entirely independent of
 * anything server-side resets. A server-only fix can never stop that -
 * only the affected player's own client can, by clearing that KeyMapping
 * directly. Hence this new sync: it lets the picked player's own client
 * know to suppress the key every client tick (see ClientPickedState +
 * CuffedAddonKeybinds' onClientTick).
 */
public record PlayerPickedSelfSyncPacket(boolean picked) {

    public static void encode(PlayerPickedSelfSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.picked());
    }

    public static PlayerPickedSelfSyncPacket decode(FriendlyByteBuf buf) {
        return new PlayerPickedSelfSyncPacket(buf.readBoolean());
    }

    public static void handle(PlayerPickedSelfSyncPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> ClientPickedState.setPicked(packet.picked()));
        ctx.setPacketHandled(true);
    }
}
