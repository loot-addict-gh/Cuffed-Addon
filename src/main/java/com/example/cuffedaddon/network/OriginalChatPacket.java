package com.example.cuffedaddon.network;

import com.example.cuffedaddon.ServerEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record OriginalChatPacket(String text) {

    public static void encode(OriginalChatPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.text(), 32767);
    }

    public static OriginalChatPacket decode(FriendlyByteBuf buf) {
        return new OriginalChatPacket(buf.readUtf(32767));
    }

    public static void handle(OriginalChatPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender != null) {
                ServerEvents.queueOriginal(sender.getUUID(), packet.text());
            }
        });
        ctx.setPacketHandled(true);
    }
}
