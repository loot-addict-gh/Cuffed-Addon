package com.example.cuffedaddon.trap;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.example.cuffedaddon.CuffedAddon;
import com.example.cuffedaddon.config.CuffedAddonServerConfig;

/**
 * Drives {@link RedstoneTrapUtil} from the two things that can change a trap's
 * answer: the redstone around it, and somebody walking into it.
 *
 * <h2>Redstone changes - deferred by one tick, on purpose</h2>
 * {@code NeighborNotifyEvent} tells us a block changed and which sides it
 * notified; any trap on one of those sides may now be powered or unpowered. The
 * positions are QUEUED rather than evaluated on the spot, for the same reason
 * {@code ReinforcedBarsColumnFix} defers its own work: evaluating a trap calls
 * {@code level.setBlock}, which synchronously fires more
 * {@code NeighborNotifyEvent}s, which would mutate the queue mid-iteration. It
 * also lets a redstone update settle before we read
 * {@code hasNeighborSignal} off it.
 *
 * <h2>Somebody walking in - a slow poll around each player</h2>
 * A trap that is already powered gets no block update when a player strolls into
 * it, so an edge-triggered design would only ever catch people who were standing
 * there when the lever flipped. [stated] asked for the opposite: "a powered
 * pillory / wall restraint should check for a player no matter how long theyve
 * been powered on". Polling around each PLAYER rather than over every trap in
 * the world keeps that cheap and bounded - a few dozen block reads per player
 * twice a second, with no registry of trap positions to persist, lose, or
 * rebuild after a restart.
 */
@Mod.EventBusSubscriber(modid = CuffedAddon.MODID)
public final class RedstoneTrapEvents {

    /** Ticks between player-proximity sweeps. 10 = twice a second. */
    private static final int POLL_INTERVAL_TICKS = 10;

    /** How far around a player to look for a trap, in blocks. */
    private static final int POLL_RADIUS = 1;
    private static final int POLL_BELOW = 1;
    private static final int POLL_ABOVE = 2;

    private static final Map<ServerLevel, Set<BlockPos>> PENDING = new HashMap<>();

    private static int tickCounter;

    private RedstoneTrapEvents() {
    }

    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !CuffedAddonServerConfig.TRAP_REDSTONE_ENABLED.get()) {
            return;
        }
        // getPos()/getState() describe the block that CHANGED, not the block
        // being notified - so look outward at the sides it notified, plus the
        // changed block's own position in case a trap block itself changed.
        queueIfTrap(level, event.getPos());
        for (Direction side : event.getNotifiedSides()) {
            queueIfTrap(level, event.getPos().relative(side));
        }
    }

    private static void queueIfTrap(ServerLevel level, BlockPos pos) {
        if (RedstoneTrapUtil.isTrapBlock(level.getBlockState(pos))) {
            PENDING.computeIfAbsent(level, l -> new LinkedHashSet<>()).add(pos.immutable());
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        drainPending();

        if (++tickCounter < POLL_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;
        if (!CuffedAddonServerConfig.TRAP_REDSTONE_ENABLED.get()) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                sweepAround(level, player.blockPosition());
            }
        }
    }

    /**
     * Snapshot and clear before evaluating: evaluating writes block states,
     * which re-enters onNeighborNotify synchronously. Anything queued that way
     * lands in a fresh map and is handled next tick instead of corrupting this
     * iteration.
     */
    private static void drainPending() {
        if (PENDING.isEmpty()) {
            return;
        }
        Map<ServerLevel, Set<BlockPos>> batch = new HashMap<>(PENDING);
        PENDING.clear();
        for (Map.Entry<ServerLevel, Set<BlockPos>> entry : batch.entrySet()) {
            for (BlockPos pos : entry.getValue()) {
                RedstoneTrapUtil.evaluate(entry.getKey(), pos);
            }
        }
    }

    private static void sweepAround(ServerLevel level, BlockPos centre) {
        for (int dy = -POLL_BELOW; dy <= POLL_ABOVE; dy++) {
            for (int dx = -POLL_RADIUS; dx <= POLL_RADIUS; dx++) {
                for (int dz = -POLL_RADIUS; dz <= POLL_RADIUS; dz++) {
                    BlockPos pos = centre.offset(dx, dy, dz);
                    if (RedstoneTrapUtil.isTrapBlock(level.getBlockState(pos))) {
                        RedstoneTrapUtil.evaluate(level, pos);
                    }
                }
            }
        }
    }
}
