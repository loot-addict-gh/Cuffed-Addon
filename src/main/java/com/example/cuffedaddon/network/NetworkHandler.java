package com.example.cuffedaddon.network;

import net.minecraftforge.fml.ModList;
import com.example.cuffedaddon.CuffedAddon;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public class NetworkHandler {

    /**
     * The channel version is THIS ADDON'S MOD VERSION, so a client and server
     * running different builds are refused at the handshake (1.5.19).
     *
     * <p>[stated]: <i>"id rather mismatches be rejected outright for every new
     * version we make, just in case"</i>. It used to be a hardcoded {@code "1"}
     * that never changed, which meant a mismatch was never detected - and packet
     * ids here are handed out by a running counter, so a build that added or
     * moved a packet could have its messages read as a different packet entirely.
     * This addon's jar filename does not carry its version either, so a stale
     * copy on one side is genuinely easy to miss; [stated] lost a round to
     * exactly that shape of problem with another mod.
     *
     * <p>Read from the mod's own metadata rather than a constant somebody has to
     * remember to bump, which is the failure this is meant to prevent. Resolved
     * lazily and cached: the supplier below is only called during the network
     * handshake, long after mod loading, so {@code ModList} is fully built by
     * then - it would NOT be safe to call this from the mod constructor, where
     * this class is first touched.
     *
     * <p>The fallback exists so a lookup failure can never stop the mod loading.
     * It is the same string on both sides, so two installs that both fall back
     * still agree - they just lose the mismatch check they would otherwise have.
     */
    private static String protocolVersion;

    private static synchronized String protocolVersion() {
        if (protocolVersion == null) {
            protocolVersion = ModList.get()
                    .getModContainerById(CuffedAddon.MODID)
                    .map(container -> container.getModInfo().getVersion().toString())
                    .orElse("unknown");
        }
        return protocolVersion;
    }

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(CuffedAddon.MODID, "main"),
            NetworkHandler::protocolVersion,
            // Both sides must match exactly. A vanilla or mod-less counterpart is
            // refused too, which is correct: this addon is mandatory on both
            // sides in mods.toml.
            remote -> protocolVersion().equals(remote),
            remote -> protocolVersion().equals(remote)
    );

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(
                id++,
                OriginalChatPacket.class,
                OriginalChatPacket::encode,
                OriginalChatPacket::decode,
                OriginalChatPacket::handle
        );
        // NOTE: BundleMuffleSyncPacket used to sit here, between the chat
        // packet and the pose packets. Removed in 1.4.41 with the bundleMuffle
        // gamerule itself. The ids are assigned by `id++` rather than written
        // out, so everything after it simply shifts down by one - harmless,
        // because a client and server always run the same build of this addon
        // (the channel's PROTOCOL_VERSION check enforces that on connect).
        CHANNEL.registerMessage(
                id++,
                LiePoseSyncPacket.class,
                LiePoseSyncPacket::encode,
                LiePoseSyncPacket::decode,
                LiePoseSyncPacket::handle
        );
        CHANNEL.registerMessage(
                id++,
                WallPoseSyncPacket.class,
                WallPoseSyncPacket::encode,
                WallPoseSyncPacket::decode,
                WallPoseSyncPacket::handle
        );
        CHANNEL.registerMessage(
                id++,
                ReleaseRestraintPacket.class,
                ReleaseRestraintPacket::encode,
                ReleaseRestraintPacket::decode,
                ReleaseRestraintPacket::handle
        );
        CHANNEL.registerMessage(
                id++,
                PlayerPickedSelfSyncPacket.class,
                PlayerPickedSelfSyncPacket::encode,
                PlayerPickedSelfSyncPacket::decode,
                PlayerPickedSelfSyncPacket::handle
        );
        // Shock Collar (1.4.32). The sync packet goes to trackers AND the
        // wearer (capabilities don't sync themselves, and the collar has to be
        // visible on other players); the struggle packet is client -> server
        // and carries no payload, since everything it claims is re-validated
        // server-side.
        CHANNEL.registerMessage(
                id++,
                ShockCollarSyncPacket.class,
                ShockCollarSyncPacket::encode,
                ShockCollarSyncPacket::decode,
                ShockCollarSyncPacket::handle
        );
        CHANNEL.registerMessage(
                id++,
                ShockCollarStrugglePacket.class,
                ShockCollarStrugglePacket::encode,
                ShockCollarStrugglePacket::decode,
                ShockCollarStrugglePacket::handle
        );
        // Fake Players compatibility (1.5.0). Registered unconditionally even
        // though that mod is optional: a packet type costs nothing when nothing
        // sends it, and making registration conditional would make the channel's
        // packet ids depend on which mods are installed, which is exactly how a
        // client and server end up disagreeing about what id means what.
        // The direction is declared, not left open. This packet's handler calls
        // Minecraft.getInstance(), and net.minecraft.client.Minecraft does not
        // exist on a dedicated server - so a client that sent this packet to a
        // server that accepted it would throw NoClassDefFoundError on the server
        // thread. Declaring PLAY_TO_CLIENT makes the channel reject it on the way
        // in instead. (The addon's other server-to-client packets are registered
        // without a direction too and have the same shape; they are left as they
        // are for now rather than changed in a round about something else.)
        CHANNEL.registerMessage(
                id++,
                FakeRestraintSyncPacket.class,
                FakeRestraintSyncPacket::encode,
                FakeRestraintSyncPacket::decode,
                FakeRestraintSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
        CHANNEL.registerMessage(
                id++,
                FakeDetainedSyncPacket.class,
                FakeDetainedSyncPacket::encode,
                FakeDetainedSyncPacket::decode,
                FakeDetainedSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
        // Illusion (1.5.18). Appended at the END on purpose: ids are handed out by
        // a running id++, so inserting one in the middle silently renumbers every
        // packet after it. That is only harmless while client and server run the
        // same build, and this project has already had one round lost to a stale
        // jar on the server that looked identical in a folder listing.
        CHANNEL.registerMessage(
                id++,
                IllusionLegsSyncPacket.class,
                IllusionLegsSyncPacket::encode,
                IllusionLegsSyncPacket::decode,
                IllusionLegsSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }
}
