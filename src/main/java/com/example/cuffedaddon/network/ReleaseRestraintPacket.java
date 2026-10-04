package com.example.cuffedaddon.network;

import com.lazrproductions.cuffed.restraints.base.RestraintType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> server: sent by CuffedAddonKeybinds when one of the 3 "Release
 * Arms/Head/Legs" keybinds is pressed. [stated] explicitly asked for these
 * keybinds to use Cuffed's own real `/cuffed <player> remove <type>`
 * command (confirmed real syntax + real permission requirement by cloning
 * Cuffed's actual source, command/HandcuffCommand.java) - so this handler
 * literally dispatches that command text through the server's own command
 * dispatcher, targeting the SENDING player's own username (self-release
 * only, per [stated]'s confirmed answer to an explicit clarifying
 * question - NOT whoever the sender is looking at).
 *
 * Cuffed's own command requires `source.hasPermission(3)` (OP) - by
 * design, since it's meant as an admin/debug tool. [stated] initially got
 * a version of this that bypassed that check (an elevated
 * CommandSourceStack) so any player could use their own self-release
 * keybind regardless of OP status, then explicitly corrected that: these
 * keybinds should be OP-GATED, same as typing the command yourself. Fixed
 * by dispatching through the sender's own REAL CommandSourceStack
 * (`sender.createCommandSourceStack()`, no permission override) - a
 * non-OP player pressing the keybind gets exactly the same "you don't have
 * permission" failure Brigadier gives for typing an unauthorized command,
 * and only an OP'd player's press actually releases anything.
 */
public record ReleaseRestraintPacket(RestraintType type) {

    public static void encode(ReleaseRestraintPacket packet, FriendlyByteBuf buf) {
        buf.writeEnum(packet.type());
    }

    public static ReleaseRestraintPacket decode(FriendlyByteBuf buf) {
        return new ReleaseRestraintPacket(buf.readEnum(RestraintType.class));
    }

    public static void handle(ReleaseRestraintPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) return;

            MinecraftServer server = sender.getServer();
            if (server == null) return;

            CommandSourceStack source = sender.createCommandSourceStack();
            String command = "cuffed " + sender.getGameProfile().getName() + " remove " + packet.type().name();
            server.getCommands().performPrefixedCommand(source, command);
        });
        ctx.setPacketHandled(true);
    }
}
