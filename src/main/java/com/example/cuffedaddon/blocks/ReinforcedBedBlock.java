package com.example.cuffedaddon.blocks;

import com.lazrproductions.cuffed.blocks.BunkBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * A colored "Reinforced Bed" variant - identical gameplay to Cuffed's own
 * `cuffed:bunk` (this class extends BunkBlock directly and changes
 * nothing about its use/shape/placement/etc logic), just a different
 * registered Block instance so each color can have its own texture via
 * its own blockstate/model.
 *
 * IMPORTANT: overrides newBlockEntity() to return null, rather than
 * inheriting BunkBlock's real implementation (which unconditionally
 * constructs a BunkBlockEntity). BunkBlockEntity's constructor is
 * hardcoded to Cuffed's own registered BlockEntityType
 * (ModBlockEntities.BUNK_BLOCK_ENTITY), which in turn is built with
 * BlockEntityType.Builder.of(..., ModBlocks.BUNK.get()) - i.e. it only
 * lists Cuffed's own cuffed:bunk block as a valid block for that type.
 * Any of these new Block instances would produce a block-entity/blockstate
 * TYPE MISMATCH if they used the real BunkBlockEntity (Forge validates a
 * loaded block entity's type against the block actually present at that
 * position). BunkBlockEntity itself holds no data and never ticks (see
 * Cuffed's real source - it's essentially empty, only exists because
 * BunkBlock implements EntityBlock), so skipping it entirely here is safe
 * and avoids the mismatch risk rather than registering a whole new,
 * equally-pointless BlockEntityType just to satisfy the interface.
 */
public class ReinforcedBedBlock extends BunkBlock {
    public ReinforcedBedBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@javax.annotation.Nonnull BlockPos pos, @javax.annotation.Nonnull BlockState state) {
        return null;
    }
}
