package com.example.cuffedaddon.trap;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockSource;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.AABB;

import com.lazrproductions.cuffed.restraints.base.RestraintType;

/**
 * Geometry shared by every dispenser trap: which block a dispenser is aimed at,
 * who is standing in it, and which body part that adds up to.
 *
 * <h2>The slot comes from the GEOMETRY, not from the item</h2>
 * This is the one place this addon deliberately disagrees with Cuffed. Cuffed's
 * own {@code AbstractRestraintItem#dispenseRestraint} picks the slot from the
 * ITEM's class (a head-restraint item goes on the head no matter where the
 * dispenser is), falling back to a waist test only for its three "ambiguous"
 * items. [stated]'s spec for these traps is the opposite and much more
 * predictable: <b>where you aim the dispenser decides which body part you are
 * aiming at</b>, and an item that cannot go there is simply spat out -
 * "if the restraint item cannot be applied to the specific slot (e.g. fuzzy
 * handcuffs on legs or head) it should be spitted out instead".
 *
 * <p>So a Sleep Mask in a side-on dispenser spits out, because a side-on
 * dispenser is aiming at arms or legs, and a head restraint has no business
 * there. Aim it downward at someone's head and it goes on. That is intended.
 *
 * <h2>The height rule</h2>
 * A player is about two blocks tall, so a horizontal dispenser is aimed at one
 * of their two halves:
 * <ul>
 *   <li>the cell their feet are in -&gt; {@link RestraintType#Leg};</li>
 *   <li>the cell above that -&gt; {@link RestraintType#Arm};</li>
 * </ul>
 * A dispenser facing DOWN is above them, so it reaches the head. A dispenser
 * facing UP is in the floor they are standing on, so it reaches the legs -
 * [stated]'s explicit choice when asked, and what makes a pressure-plate style
 * floor trap work.
 */
public final class TrapDispenseUtil {

    private TrapDispenseUtil() {
    }

    /** The single block a dispenser is aimed at. */
    public static BlockPos targetPos(BlockSource source) {
        return source.getPos().relative(source.getBlockState().getValue(DispenserBlock.FACING));
    }

    public static Direction facing(BlockSource source) {
        return source.getBlockState().getValue(DispenserBlock.FACING);
    }

    /**
     * The first live, non-spectator player standing in the aimed-at block.
     *
     * <p>Uses {@code new AABB(blockpos)} exactly as Cuffed's own dispenser path
     * does, so "is this player in front of the dispenser" means the same thing
     * for our traps as it already does for handcuffs.
     */
    @Nullable
    public static ServerPlayer playerAt(ServerLevel level, BlockPos pos) {
        // EntitySelector.NO_SPECTATORS alone, which is exactly the predicate
        // Cuffed's own dispenser path uses, plus a plain isAlive() test written
        // out rather than EntitySelector.ENTITY_STILL_ALIVE - that constant's
        // name could not be verified against any source available here, and an
        // unverifiable vanilla name is precisely what broke a previous release.
        List<ServerPlayer> found = level.getEntitiesOfClass(ServerPlayer.class, new AABB(pos),
                EntitySelector.NO_SPECTATORS);
        for (ServerPlayer candidate : found) {
            if (candidate.isAlive()) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Which body part a dispenser aimed at {@code targetPos} is addressing on
     * {@code player}.
     *
     * @return the slot, or null if this facing addresses no slot at all.
     */
    @Nullable
    public static RestraintType slotFor(Direction facing, BlockPos targetPos, ServerPlayer player) {
        if (facing == Direction.DOWN) {
            return RestraintType.Head;
        }
        if (facing == Direction.UP) {
            return RestraintType.Leg;
        }
        // Horizontal: compare the aimed-at cell against the cell the player's
        // feet are actually in. Deliberately NOT a "waist height" float test -
        // the spec is stated in whole blocks ("the bottom block" / "the top
        // block"), and floor(getY()) is exactly that, with none of the
        // half-block rounding trouble that partial-height blocks cause.
        int feetY = Mth.floor(player.getY());
        return targetPos.getY() <= feetY ? RestraintType.Leg : RestraintType.Arm;
    }
}
