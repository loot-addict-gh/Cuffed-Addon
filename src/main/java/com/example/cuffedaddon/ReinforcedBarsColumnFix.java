package com.example.cuffedaddon;

import com.example.cuffedaddon.gamerule.ModGameRules;
import com.lazrproductions.cuffed.blocks.ReinforcedBarsBlock;
import com.lazrproductions.cuffed.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Corrects a bug in Cuffed's own ReinforcedBarsBlock: when deciding whether a
 * bar segment should render using the top/middle/bottom texture ("column"),
 * the block's "up" check accidentally looks at the block BELOW instead of
 * ABOVE when testing for a reinforced_bars_gap ("window") neighbor. In
 * practice, a solid bar directly above a window always renders as middle,
 * and a solid bar directly below a window can only ever render as top or
 * bottom -- never middle -- regardless of what's really above/below the
 * whole stack.
 *
 * Gated behind the fixClearBars gamerule (default false, see ModGameRules)
 * since it depends on Cuffed's current internals rather than a public API.
 *
 * Rather than mixin into Cuffed's exact method -- which would only keep
 * working as long as Cuffed's compiled bytecode for that method doesn't
 * shift -- this listens for BlockEvent.NeighborNotifyEvent whenever any of
 * the three connecting block types (bars, window, cell door) changes,
 * queues that position's own vertical neighbors, and on the NEXT server
 * tick (not immediately) recomputes the correct column value for each
 * queued bar using only public block registry entries, overwriting it if
 * Cuffed's own logic got it wrong. Note that NeighborNotifyEvent's
 * getPos()/getState() describe the block that changed, not the neighbor
 * being notified -- so this reacts to the change itself and looks at its
 * neighbors, rather than filtering on the notified block's own type.
 *
 * The one-tick delay is deliberate: Cuffed's own connecting-block "shape
 * update" logic runs through a different internal vanilla code path than
 * the NeighborNotifyEvent this reacts to (updateShape vs. neighborChanged),
 * so relying on them running in a specific order within the same tick would
 * be fragile. Waiting until the start of the next tick guarantees Cuffed's
 * own logic has already run and settled, at the cost of the correction
 * being invisible for about 50ms, which isn't noticeable in normal play.
 */
@Mod.EventBusSubscriber(modid = CuffedAddon.MODID)
public final class ReinforcedBarsColumnFix {

    private static final Map<ServerLevel, Set<BlockPos>> PENDING = new HashMap<>();

    private ReinforcedBarsColumnFix() {
    }

    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!serverLevel.getGameRules().getBoolean(ModGameRules.FIX_CLEAR_BARS)) {
            return;
        }
        // event.getPos()/getState() describe the block that just changed, not
        // the neighbor being notified -- so react to a change on any of the
        // three connecting block types (bars, window, cell door), and queue
        // its own vertical neighbors for a recheck, rather than requiring the
        // changed block itself to be a ReinforcedBarsBlock.
        if (!isConnective(event.getState())) {
            return;
        }

        BlockPos pos = event.getPos();
        queue(serverLevel, pos.above());
        queue(serverLevel, pos.below());
    }

    private static void queue(ServerLevel level, BlockPos pos) {
        PENDING.computeIfAbsent(level, l -> new LinkedHashSet<>()).add(pos.immutable());
    }

    private static boolean isConnective(BlockState state) {
        return state.is(ModBlocks.REINFORCED_BARS.get())
                || state.is(ModBlocks.CELL_DOOR.get())
                || state.is(ModBlocks.REINFORCED_BARS_GAPPED.get());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }

        // Snapshot and clear first: applying a fix below calls level.setBlock,
        // which notifies neighbors and can trigger new NeighborNotifyEvent
        // calls synchronously -- those would otherwise try to mutate PENDING
        // while this loop is still iterating it. Clearing first means any
        // such re-entrant additions land in a fresh map instead.
        Map<ServerLevel, Set<BlockPos>> batch = new HashMap<>(PENDING);
        PENDING.clear();

        for (Map.Entry<ServerLevel, Set<BlockPos>> entry : batch.entrySet()) {
            ServerLevel level = entry.getKey();
            for (BlockPos pos : entry.getValue()) {
                fixColumn(level, pos);
            }
        }
    }

    private static void fixColumn(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return;
        }

        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ReinforcedBarsBlock)) {
            return;
        }

        int correctColumn = computeCorrectColumn(level, pos);
        if (state.getValue(ReinforcedBarsBlock.COLUMN) != correctColumn) {
            level.setBlock(pos, state.setValue(ReinforcedBarsBlock.COLUMN, correctColumn), 3);
        }
    }

    /**
     * Same up/down/column logic as ReinforcedBarsBlock itself, with the
     * above/below mixup fixed: both the "up" and "down" checks now correctly
     * look only in their own direction.
     */
    private static int computeCorrectColumn(Level level, BlockPos pos) {
        BlockState aboveState = level.getBlockState(pos.above());
        BlockState belowState = level.getBlockState(pos.below());

        boolean up = aboveState.is(ModBlocks.REINFORCED_BARS.get())
                || aboveState.is(ModBlocks.CELL_DOOR.get())
                || aboveState.is(ModBlocks.REINFORCED_BARS_GAPPED.get());
        boolean down = belowState.is(ModBlocks.REINFORCED_BARS.get())
                || belowState.is(ModBlocks.CELL_DOOR.get())
                || belowState.is(ModBlocks.REINFORCED_BARS_GAPPED.get());

        int column = 0;
        if (up && down) {
            column = 1;
        }
        if (down && !up) {
            column = 2;
        }
        return column;
    }
}
