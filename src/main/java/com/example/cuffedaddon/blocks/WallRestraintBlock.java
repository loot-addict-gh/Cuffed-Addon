package com.example.cuffedaddon.blocks;

import com.example.cuffedaddon.fakeplayer.FakeStationaryUtil;
import com.example.cuffedaddon.pose.WallPoseUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A 1-block-wide, 2-block-tall standing restraint panel - i.e. genuinely
 * door-sized (not just door-THICKNESS), modeled directly on vanilla
 * DoorBlock:
 *  - HALF (vanilla's own DoubleBlockHalf - LOWER/UPPER) pairs the two
 *    cells vertically, exactly like a real door. Breaking either half
 *    breaks both; only LOWER drops the item (see the loot table).
 *  - The per-cell SHAPE is a plain door-thickness (3px) slab flush against
 *    the back edge of the cell, full 16px tall within that cell - "same
 *    hitbox as a door (without the opening and closing mechanics)". Stacked
 *    LOWER+UPPER this reads as one continuous 2-tall thin panel, same as a
 *    real door's own two halves do.
 *
 * (Earlier draft of this block paired two cells side-by-side instead,
 * misreading "2 block wide" - corrected per explicit follow-up: it's 2
 * blocks TALL, 1 wide, i.e. actual door dimensions.)
 *
 * FACING is the direction the panel faces OUTWARD - i.e. the direction a
 * restrained player ends up looking, away from the panel.
 *
 * OCCUPIED still mirrors BedBlock.OCCUPIED mechanically (tracks whether
 * someone is currently restrained here, blocking a second use and driving
 * the release path) but no longer switches texture/model - both states
 * use the same wall_restraint_top/bottom textures (an earlier red/blue
 * split was dropped per explicit request once the real texture was ready).
 */
public class WallRestraintBlock extends Block {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final BooleanProperty OCCUPIED = BlockStateProperties.OCCUPIED;
    /**
     * Redstone trap state, 1.5.13. See {@code trap.TrapBlockStates#ARMED} for
     * what "armed" means and why it is a real block state property rather than
     * a capability - short version: so an observer can see the trap fire and
     * reset. The pillory gets the same property grafted on by a mixin, because
     * that block belongs to Cuffed; this one is ours, so it just declares it.
     */
    public static final BooleanProperty ARMED = com.example.cuffedaddon.trap.TrapBlockStates.ARMED;

    // Door-leaf thickness (3/16 of a block), flush against the edge OPPOSITE
    // the direction FACING points - the panel sits at the "back" of the
    // cell, and the open 13/16 in front of it is where the standing
    // player's feet actually land. Same per-facing box set a vanilla closed
    // door uses, applied identically to both the LOWER and UPPER cell.
    private static final VoxelShape SHAPE_NORTH = Shapes.box(0.0, 0.0, 0.8125, 1.0, 1.0, 1.0);
    private static final VoxelShape SHAPE_SOUTH = Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, 0.1875);
    private static final VoxelShape SHAPE_WEST = Shapes.box(0.8125, 0.0, 0.0, 1.0, 1.0, 1.0);
    private static final VoxelShape SHAPE_EAST = Shapes.box(0.0, 0.0, 0.0, 0.1875, 1.0, 1.0);

    public WallRestraintBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(OCCUPIED, false)
                .setValue(ARMED, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, OCCUPIED, ARMED);
    }

    @Nonnull
    @Override
    public VoxelShape getShape(@Nonnull BlockState state, @Nonnull net.minecraft.world.level.BlockGetter level,
                                @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            case EAST -> SHAPE_EAST;
            default -> SHAPE_NORTH;
        };
    }

    /** The other cell of this same panel - above if this is LOWER, below if this is UPPER. */
    public static BlockPos getOtherHalfPos(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
    }

    public static BlockPos getPrimaryPos(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
    }

    /**
     * BUG FIX (this round): the original version of this method required
     * the OTHER half to already exist with a matching-but-opposite HALF
     * value - which sounds right for "structural integrity" but is wrong
     * for the very first placement check: BlockItem.canPlace() calls
     * canSurvive() on the candidate LOWER state BEFORE setPlacedBy ever
     * runs to create the UPPER half, so that check could never pass and
     * the block silently failed to place anywhere, every time. Fixed to
     * match how vanilla DoorBlock actually does it: LOWER only ever checks
     * the floor below is sturdy (never looks at the other half at all);
     * UPPER checks that the block below it simply IS this block (true as
     * soon as the LOWER half exists, which by the time UPPER's own
     * canSurvive is ever queried, it already does).
     */
    @Override
    public boolean canSurvive(@Nonnull BlockState state, @Nonnull LevelReader level, @Nonnull BlockPos pos) {
        BlockPos belowPos = pos.below();
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            return level.getBlockState(belowPos).isFaceSturdy(level, belowPos, Direction.UP);
        }
        return level.getBlockState(belowPos).is(this);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos lowerPos = context.getClickedPos();
        BlockPos upperPos = lowerPos.above();

        Level level = context.getLevel();
        if (!level.getBlockState(upperPos).canBeReplaced(context)) {
            return null; // no room for the upper half - refuse placement entirely, same as a real door
        }

        Direction facing = context.getHorizontalDirection().getOpposite();
        return this.defaultBlockState().setValue(FACING, facing).setValue(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void setPlacedBy(@Nonnull Level level, @Nonnull BlockPos pos, @Nonnull BlockState state,
                             @Nullable LivingEntity placer, @Nonnull ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    @Override
    public void onRemove(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos,
                          @Nonnull BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockPos otherPos = getOtherHalfPos(state, pos);
            BlockState otherState = level.getBlockState(otherPos);
            if (otherState.is(this) && otherState.getValue(HALF) != state.getValue(HALF)) {
                // Anyone currently restrained here is freed rather than left
                // with a dangling lockedPrimaryPos pointing at an air block.
                WallPoseUtil.releaseWhoeverIsAt(level, getPrimaryPos(state, pos));
                // 1.5.11: and whichever FAKE player might be locked here instead.
                // Kept as a separate call rather than folded into the line above so
                // the pose package goes on knowing nothing about Fake Players.
                FakeStationaryUtil.releaseWallWhoeverIsAt(level, getPrimaryPos(state, pos));
                level.setBlock(otherPos, Blocks.AIR.defaultBlockState(), 35 | (isMoving ? 64 : 0));
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public void neighborChanged(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos,
                                 @Nonnull Block block, @Nonnull BlockPos fromPos, boolean isMoving) {
        BlockPos otherPos = getOtherHalfPos(state, pos);
        if (fromPos.equals(otherPos) && !level.getBlockState(otherPos).is(this)) {
            // Other half got removed by something other than our own
            // onRemove (e.g. a piston, or direct world-edit) - self-destruct
            // so a lone orphaned half can't linger with no pair.
            level.destroyBlock(pos, true);
            return;
        }
        // Floor removed out from under the LOWER half (or, via the UPPER
        // half's own canSurvive, the LOWER half itself gone missing) - pop
        // it like a real door would, rather than leaving it floating.
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
            return;
        }
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
    }

    /**
     * Right-clicking the panel: if a player is standing at its base (the
     * LOWER cell's own X/Z/Y - see WallPoseUtil.findStandingPlayer) and
     * nobody's currently restrained here, restrain them. Restraining
     * yourself or restraining someone else both go through this same path -
     * "someone (the player themselves or another player) right clicks the
     * Wall Restraint" - the ACTOR clicking and the TARGET being restrained
     * are looked up independently.
     *
     * Releasing is NOT handled here - see WallPoseEvents.onInteractWithPosedTarget
     * (Handcuffs Key on the restrained player, same as Bed Restraint).
     */
    @Nonnull
    @Override
    public InteractionResult use(@Nonnull BlockState state, Level level, @Nonnull BlockPos pos,
                                  @Nonnull Player player, @Nonnull InteractionHand hand,
                                  @Nonnull BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }

        BlockPos lowerPos = getPrimaryPos(state, pos);
        BlockState lowerState = level.getBlockState(lowerPos);
        if (!lowerState.is(this)) {
            return InteractionResult.PASS; // paired half missing somehow - do nothing rather than crash
        }
        if (lowerState.getValue(OCCUPIED)) {
            return InteractionResult.PASS; // already in use - defer to the release path
        }

        if (!(player instanceof ServerPlayer actor)) {
            return InteractionResult.PASS;
        }

        return attemptCatch(level, lowerPos, lowerState)
                ? InteractionResult.CONSUME
                : InteractionResult.FAIL;
    }

    /**
     * Restrain whoever is standing at this panel's base, and mark both halves
     * OCCUPIED. Extracted from {@link #use} in 1.5.13 so the redstone trap fires
     * through the EXACT same path a right-click does - same standing lookup,
     * same fake-player fallback, same OCCUPIED bookkeeping. A trap that behaved
     * even slightly differently from the manual case would be a second thing to
     * debug forever.
     *
     * @param lowerPos   the LOWER half's position (use {@link #getPrimaryPos})
     * @param lowerState that half's state, already confirmed to be this block
     * @return true if somebody is now restrained here
     */
    public static boolean attemptCatch(Level level, BlockPos lowerPos, BlockState lowerState) {
        if (lowerState.getValue(OCCUPIED)) {
            return false;
        }
        Direction facing = lowerState.getValue(FACING);

        // A real player at the panel's base wins; a Fake Players fake player standing
        // there is the 1.5.11 fallback. Real players are checked first so this block's
        // existing behaviour is untouched whenever one is present, and the fake-player
        // lookup is the same plain column match (see FakeStationaryUtil).
        ServerPlayer target = WallPoseUtil.findStandingPlayer(level, lowerPos);
        boolean applied;
        if (target != null) {
            applied = WallPoseUtil.tryApply(target, lowerPos, facing);
        } else {
            LivingEntity fake = FakeStationaryUtil.findStandingFakePlayer(level, lowerPos);
            if (fake == null) {
                return false; // nobody standing at the panel's base
            }
            applied = FakeStationaryUtil.tryWall(fake, lowerPos, facing);
            FakeStationaryUtil.reportFirstApply("wall restraint", applied);
        }

        if (!applied) {
            return false;
        }

        BlockPos upperPos = lowerPos.above();
        level.setBlock(lowerPos, lowerState.setValue(OCCUPIED, true), 3);
        level.setBlock(upperPos, level.getBlockState(upperPos).setValue(OCCUPIED, true), 3);
        return true;
    }

    @Override
    public boolean isPathfindable(@Nonnull BlockState state, @Nonnull net.minecraft.world.level.BlockGetter level,
                                   @Nonnull BlockPos pos, @Nonnull net.minecraft.world.level.pathfinder.PathComputationType type) {
        return false;
    }
}
