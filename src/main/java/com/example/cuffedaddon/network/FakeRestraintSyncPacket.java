package com.example.cuffedaddon.network;

import com.example.cuffedaddon.capability.ModCapabilities;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client, broadcast to everyone tracking a fake player whenever its
 * restraints change, plus a targeted send when a client starts tracking one.
 *
 * <p>Needed for the same reason {@code ShockCollarSyncPacket} is: Forge
 * capabilities do not sync themselves, and this state decides how the entity
 * renders on everyone else's screen.
 *
 * <p>The whole capability goes over as one CompoundTag rather than six fields.
 * It is three ids and three flags, it only moves when somebody applies or removes
 * a restraint, and shipping the serialised form means the wire format cannot drift
 * away from the saved format.
 */
public class FakeRestraintSyncPacket {

    private final int entityId;
    private final CompoundTag state;

    public FakeRestraintSyncPacket(int entityId, CompoundTag state) {
        this.entityId = entityId;
        this.state = state;
    }

    public static void encode(FakeRestraintSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeNbt(packet.state);
    }

    public static FakeRestraintSyncPacket decode(FriendlyByteBuf buf) {
        return new FakeRestraintSyncPacket(buf.readVarInt(), buf.readNbt());
    }

    public static void handle(FakeRestraintSyncPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null || packet.state == null) {
                return;
            }
            Entity entity = minecraft.level.getEntity(packet.entityId);
            if (entity == null) {
                return;
            }
            // The capability is attached on BOTH sides (AttachCapabilitiesEvent
            // fires wherever the entity is constructed), so mirroring the tag
            // onto the client copy is all the render layers need - they read it
            // through IRestrainableEntity, which FakePlayerEntityMixin backs
            // with this same capability.
            entity.getCapability(ModCapabilities.FAKE_RESTRAINED)
                    .ifPresent(cap -> cap.deserializeNBT(packet.state));
        });
        ctx.setPacketHandled(true);
    }
}
