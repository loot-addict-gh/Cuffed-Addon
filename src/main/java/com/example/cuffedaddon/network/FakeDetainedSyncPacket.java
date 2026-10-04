package com.example.cuffedaddon.network;

import com.example.cuffedaddon.fakeplayer.FakeStationaryUtil;
import com.example.cuffedaddon.fakeplayer.IFakeDetained;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client pillory state for one fake player.
 *
 * <h2>Why the whole tag instead of individual fields</h2>
 * Mirrors {@code FakeRestraintSyncPacket}: the capability already knows how to
 * write and read itself, so shipping {@code serializeNBT()} means one place
 * defines the wire format and it cannot drift from the saved format. The other
 * two stationary restraints reuse the existing {@code LiePoseSyncPacket} /
 * {@code WallPoseSyncPacket}, which predate that idea and send fields - not worth
 * churning them.
 *
 * <h2>Why the direction is declared</h2>
 * Because the handler touches {@code Minecraft.getInstance()}. An undeclared
 * direction lets the packet be sent (or spoofed) the other way, where that class
 * does not exist. {@code FakeRestraintSyncPacket} learned this in the 1.5.7 audit;
 * this one is declared from the start - see {@code NetworkHandler#register}.
 */
public class FakeDetainedSyncPacket {

    private final int entityId;
    private final CompoundTag state;

    public FakeDetainedSyncPacket(int entityId, CompoundTag state) {
        this.entityId = entityId;
        this.state = state;
    }

    public static void encode(FakeDetainedSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeNbt(packet.state);
    }

    public static FakeDetainedSyncPacket decode(FriendlyByteBuf buf) {
        return new FakeDetainedSyncPacket(buf.readVarInt(), buf.readNbt());
    }

    public static void handle(FakeDetainedSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || packet.state == null) {
                return;
            }
            Entity entity = mc.level.getEntity(packet.entityId);
            if (!(entity instanceof LivingEntity living)) {
                return;
            }
            IFakeDetained cap = FakeStationaryUtil.detained(living);
            if (cap == null) {
                return;
            }
            cap.deserializeNBT(packet.state);

            // Same instant-snap treatment the two pose packets use: moveTo, not
            // setPos, because moveTo also clears the xo/yo/zo "old position"
            // fields a queued remote-entity interpolation would otherwise still
            // animate from. See LiePoseSyncPacket's own note.
            if (cap.isDetained()) {
                living.moveTo(cap.getLockedX(), cap.getLockedY(), cap.getLockedZ(),
                        cap.getLockedYaw(), living.getXRot());
                living.setYBodyRot(cap.getLockedYaw());
                living.setYHeadRot(cap.getLockedYaw());
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
