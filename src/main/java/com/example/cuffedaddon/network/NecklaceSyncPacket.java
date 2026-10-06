package com.example.cuffedaddon.network;

import com.example.cuffedaddon.capability.ModCapabilities;
import com.example.cuffedaddon.init.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -&gt; client Key Necklace state, broadcast to everyone TRACKING the
 * wearer as well as to the wearer themselves.
 *
 * <p>Same reasoning as {@code ShockCollarSyncPacket}: Forge capabilities
 * attached through {@code AttachCapabilitiesEvent} are not synced at all, so a
 * remote player's client-side capability reads back its defaults forever, and
 * the worn necklace has to be VISIBLE on other players for
 * {@code KeyNecklaceLayer} to draw. This therefore follows the
 * broadcast-to-trackers pattern rather than the self-only one
 * {@code PlayerPickedSelfSyncPacket} uses.
 *
 * <p><b>Only a boolean crosses the wire, not the worn ItemStack.</b> Nothing on
 * the client needs the real item: the render layer asks one question ("is there
 * a necklace on this entity"), and the interact listener's both-sides branch
 * asks the same one. The client-side capability is therefore filled with a
 * plain unmodified Key Necklace as a stand-in - enough for
 * {@code isWearing()} to answer correctly - while the server keeps the actual
 * stack. Sending the stack would mean every nearby client learning the NBT of
 * something they cannot interact with, to no end.
 */
public class NecklaceSyncPacket {

    private final int entityId;
    private final boolean wearing;

    public NecklaceSyncPacket(int entityId, boolean wearing) {
        this.entityId = entityId;
        this.wearing = wearing;
    }

    public static void encode(NecklaceSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeBoolean(packet.wearing);
    }

    public static NecklaceSyncPacket decode(FriendlyByteBuf buf) {
        return new NecklaceSyncPacket(buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(NecklaceSyncPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) {
                return;
            }
            Entity entity = minecraft.level.getEntity(packet.entityId);
            // LivingEntity, not Player: a Fake Players fake player wears one too,
            // and their entity is a PathfinderMob. The capability is attached to
            // both (see NecklaceEvents#onAttachCapabilities), and the render layer
            // is LivingEntity-typed already.
            if (!(entity instanceof LivingEntity living)) {
                return;
            }
            living.getCapability(ModCapabilities.NECKLACED).ifPresent(cap ->
                    cap.setWorn(packet.wearing
                            ? new ItemStack(ModItems.KEY_NECKLACE.get())
                            : ItemStack.EMPTY));
        });
        ctx.setPacketHandled(true);
    }
}
