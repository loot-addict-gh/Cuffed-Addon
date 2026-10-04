package com.example.cuffedaddon;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

/**
 * Persists the set of block positions the Block Locker has reinforced
 * (1.3.x). Mirrors HearUnmuffledSavedData's own create/load/save pattern
 * in this same package, but is genuinely PER-LEVEL (one instance per
 * dimension, via ServerLevel#getDataStorage()) rather than stored once on
 * the overworld - unlike a player-UUID set, a BlockPos is meaningless
 * without knowing which dimension it's in, so each dimension needs its
 * own independent set.
 *
 * Positions stored as a packed long array (BlockPos#asLong/BlockPos#of) -
 * far more compact than a ListTag of compound positions, and this is
 * exactly what vanilla itself uses for its own block-position sets (e.g.
 * POI/beacon data) for the same reason.
 *
 * This does NOT by itself confer the "can't be broken without a pickaxe"
 * behavior - see ReinforcedPositionEvents, which checks this set
 * alongside Cuffed's own static reinforced_blocks tag check (that part is
 * entirely Cuffed's own, untouched).
 */
public class ReinforcedPositionsSavedData extends SavedData {
    private static final String NAME = "cuffedaddon_reinforced_positions";

    private final Set<BlockPos> positions = new HashSet<>();

    public static ReinforcedPositionsSavedData create() {
        return new ReinforcedPositionsSavedData();
    }

    public static ReinforcedPositionsSavedData load(CompoundTag tag) {
        ReinforcedPositionsSavedData data = new ReinforcedPositionsSavedData();
        long[] packed = tag.getLongArray("positions");
        for (long value : packed) {
            data.positions.add(BlockPos.of(value));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        long[] packed = new long[positions.size()];
        int i = 0;
        for (BlockPos pos : positions) {
            packed[i++] = pos.asLong();
        }
        tag.putLongArray("positions", packed);
        return tag;
    }

    public boolean isReinforced(BlockPos pos) {
        return positions.contains(pos);
    }

    public void add(BlockPos pos) {
        if (positions.add(pos.immutable())) {
            setDirty();
        }
    }

    public void remove(BlockPos pos) {
        if (positions.remove(pos)) {
            setDirty();
        }
    }

    public static ReinforcedPositionsSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                ReinforcedPositionsSavedData::load,
                ReinforcedPositionsSavedData::create,
                NAME
        );
    }
}
