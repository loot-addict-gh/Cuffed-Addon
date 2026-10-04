package com.example.cuffedaddon.items;

import com.example.cuffedaddon.ReinforcedPositionsSavedData;
import com.lazrproductions.cuffed.init.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nonnull;

/**
 * 1.3.x - "Option A" from chat: right-click a placed block to reinforce
 * that specific position (unbreakable without a pickaxe, same as Cuffed's
 * own statically-tagged reinforced blocks). Position tracking lives in
 * ReinforcedPositionsSavedData; the actual break-blocking/auto-clear
 * behavior is in ReinforcedPositionEvents, not here - this class only
 * handles the apply side.
 *
 * 16 durability (Item.Properties#durability(16) on registration, which
 * also implies stacksTo(1)) - 1 use per application, same
 * hurtAndBreak(1, ...) pattern Cuffed's own code uses elsewhere (e.g. the
 * fork/spoon crumbling handler in ModServerEvents).
 */
public class BlockLockerItem extends Item {
    public BlockLockerItem(Item.Properties properties) {
        super(properties);
    }

    @Nonnull
    @Override
    public InteractionResult useOn(@Nonnull UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();

        if (level.isClientSide || !(level instanceof ServerLevel serverLevel) || player == null) {
            return InteractionResult.SUCCESS;
        }

        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return InteractionResult.FAIL;
        }

        // Already reinforced (either statically, like Cuffed's own
        // reinforced blocks, or already dynamically reinforced by an
        // earlier use here) - nothing to do, don't waste durability.
        ReinforcedPositionsSavedData data = ReinforcedPositionsSavedData.get(serverLevel);
        if (state.is(ModTags.Blocks.REINFORCED_BLOCKS) || data.isReinforced(pos)) {
            return InteractionResult.FAIL;
        }

        data.add(pos);
        level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS);

        context.getItemInHand().hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(context.getHand()));

        return InteractionResult.SUCCESS;
    }
}
