package com.example.cuffedaddon.network;

import com.example.cuffedaddon.collar.ShockCollarUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> server: "I just made a struggle attempt against my collar."
 *
 * <p>Carries no payload at all. Everything that decides whether the attempt is
 * legitimate - am I actually collared, am I holding cutlery, do my arm
 * restraints let me use items, is breaking out even enabled in config - is
 * re-checked server-side in {@link ShockCollarUtil#canStruggle}. The client's
 * own copy of those checks exists purely so the local player gets an immediate
 * audio cue and doesn't spam packets; it is never trusted.
 *
 * <p>This mirrors how Cuffed's own breakable restraints work
 * ({@code CuffedAPI.Networking.sendRestraintUtilityPacketToServer} with opcode
 * 102), but goes through this addon's own channel because the collar is in the
 * addon's own fourth slot, not one of Cuffed's three, so none of Cuffed's
 * restraint-utility plumbing applies to it.
 */
public record ShockCollarStrugglePacket() {

    public static void encode(ShockCollarStrugglePacket packet, FriendlyByteBuf buf) {
    }

    public static ShockCollarStrugglePacket decode(FriendlyByteBuf buf) {
        return new ShockCollarStrugglePacket();
    }

    public static void handle(ShockCollarStrugglePacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender != null) {
                ShockCollarUtil.struggle(sender);
            }
        });
        ctx.setPacketHandled(true);
    }
}
