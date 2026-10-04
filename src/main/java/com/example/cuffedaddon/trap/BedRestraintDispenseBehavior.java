package com.example.cuffedaddon.trap;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import com.example.cuffedaddon.config.CuffedAddonServerConfig;
import com.example.cuffedaddon.items.BedRestraintItem;

/**
 * Dispenser trap for the Bed Restraint.
 *
 * <h2>Why this one is not redstone-driven like the pillory and wall restraint</h2>
 * [stated]'s reasoning: the Bed Restraint consumes an ITEM, so a bare redstone
 * pulse has nothing to spend - and "id imagine itd be difficult to implement a
 * redstone powered on/off on every type of bed and bunk in the game aswell",
 * which is right: beds are a whole family of vanilla and modded blocks this
 * addon does not own and cannot add a block state property to. A dispenser
 * sidesteps both problems - it holds the item, and it needs nothing from the bed
 * at all.
 *
 * <h2>Aiming</h2>
 * Deliberately looser than the other dispenser traps, because [stated] asked for
 * "a dispenser facing that bed (from the side, under or over) or the player
 * (from any direction)". So the aimed-at block qualifies two ways:
 * <ul>
 *   <li>it contains a player who is lying/standing on a bed - any direction; or</li>
 *   <li>it IS a bed, and someone is on top of that bed.</li>
 * </ul>
 * The second case is what lets a dispenser in the floor under a bed, or in the
 * ceiling over it, work without having to aim at the exact cell the sleeper's
 * body occupies.
 */
public class BedRestraintDispenseBehavior extends DefaultDispenseItemBehavior {

    @Override
    protected ItemStack execute(BlockSource source, ItemStack stack) {
        if (!CuffedAddonServerConfig.TRAP_DISPENSER_BED_ENABLED.get()) {
            return super.execute(source, stack);
        }
        ServerPlayer target = findTarget(source);
        if (target != null && BedRestraintItem.tryRestrainFromTrap(target)) {
            stack.shrink(1);
            return stack;
        }
        return super.execute(source, stack);
    }

    @Nullable
    private static ServerPlayer findTarget(BlockSource source) {
        ServerLevel level = source.getLevel();
        BlockPos aimed = TrapDispenseUtil.targetPos(source);

        // Aimed straight at the person.
        ServerPlayer direct = TrapDispenseUtil.playerAt(level, aimed);
        if (direct != null && BedRestraintItem.bedUnder(level, direct) != null) {
            return direct;
        }

        // Aimed at the bed itself. A player lying on a bed has their feet Y
        // inside the BED's own cell (beds are shorter than a full block), so
        // check that cell first and the one above it second - the same two-cell
        // probe, in the same order, that BedRestraintItem#bedUnder uses in
        // reverse.
        BlockState aimedState = level.getBlockState(aimed);
        if (!aimedState.getBlock().isBed(aimedState, level, aimed, null)) {
            return null;
        }
        for (BlockPos occupied : new BlockPos[] { aimed, aimed.above() }) {
            ServerPlayer onBed = TrapDispenseUtil.playerAt(level, occupied);
            if (onBed != null && BedRestraintItem.bedUnder(level, onBed) != null) {
                return onBed;
            }
        }
        return null;
    }
}
