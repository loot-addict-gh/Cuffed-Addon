package com.example.cuffedaddon.items;

import com.example.cuffedaddon.CuffedAddon;
import com.example.cuffedaddon.pose.LiePoseUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Two ways to use this: right-click ANOTHER player while they're standing
 * on a bed (interactLivingEntity below), or - if you're not looking at
 * another player at all - right-click a bed you're standing on yourself to
 * restrain YOURSELF (onRightClickBlock below). Works on vanilla BedBlock or
 * Cuffed's own "Bunk" - both report Block#isBed() true and share the same
 * FACING/PART property objects as vanilla, confirmed via Cuffed's real
 * source, so this needs no Cuffed-specific bed class at all. Refuses if the
 * target (whichever one applies) isn't standing on a bed, isn't actually
 * standing (crouching is refused too, permanently - a real render bug
 * (legs visibly sinking) was chased across several rounds and never
 * fully resolved; [stated] decided it's not worth pursuing further for
 * such a minor visual issue, so this precondition is the accepted
 * permanent behavior now, not a temporary workaround), or already has an
 * arm or leg restraint equipped (LiePoseUtil.setPosed's own precondition).
 * All 3 refusals are silent - no toast - per explicit request.
 *
 * Deliberately does NOT extend Cuffed's AbstractRestraintItem - this isn't
 * a "restraint type" registered with Cuffed's own RestraintAPI (no
 * durability, no break-free, no key/lockpick unlock - LiePoseEvents'
 * Handcuffs Key handling covers unlock instead); it's a standalone trigger
 * for this addon's own pose system. Head restraints can still be
 * applied/removed on the resulting posed player independently, same as any
 * other lie-posed player (see LiePoseEvents.onInteractWithPosedTarget).
 *
 * The locked position/yaw always come from the BED itself (see
 * LiePoseUtil.setPosedOnBed), never from whichever way the player/target
 * happened to be facing - so the body always ends up lying along the bed
 * with the head on the pillow side.
 */
@Mod.EventBusSubscriber(modid = CuffedAddon.MODID)
public class BedRestraintItem extends Item {

    // Generous but bounded - only used to decide "is another player in the
    // crosshair at all", not as a real interaction-range authority (vanilla's
    // own reach check already gates the actual block right-click that gets
    // here in the first place).
    private static final double SELF_APPLY_LOOK_CHECK_REACH = 5.0;

    public BedRestraintItem(Properties properties) {
        super(properties);
    }

    @Nonnull
    @Override
    public InteractionResult interactLivingEntity(@Nonnull ItemStack stack, @Nonnull Player player,
                                                    @Nonnull LivingEntity entity, @Nonnull InteractionHand hand) {
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(entity instanceof ServerPlayer target)) {
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer actor)) {
            return InteractionResult.PASS;
        }

        return tryRestrain(stack, actor, target, "Player must be standing on a bed",
                "The player must be standing, with no arm or leg restraints equipped");
    }

    /**
     * Self-application: right-clicking a bed you're standing on yourself,
     * with nothing else targeted. Handled via RightClickBlock (fires
     * BEFORE vanilla's own block-use dispatch, i.e. before BedBlock's own
     * "try to sleep" logic runs) rather than overriding useOn/use on the
     * item itself - a targeted BLOCK's own use() always runs first and can
     * consume the interaction before an Item method ever sees it, which
     * would make a real bed unreliable to self-apply on (it would often
     * eat the click first). RightClickBlock is Forge's own pre-emptive
     * hook specifically for overriding that.
     *
     * "Always check for another player first" is satisfied two ways:
     * naturally, since vanilla only fires a block-right-click event at all
     * when no closer entity was hit by the same ray (entity targeting
     * already takes priority in vanilla's own hit-testing) - and
     * defensively, via an explicit entity raycast here too
     * (isLookingAtAnotherPlayer), in case that natural ordering doesn't
     * hold for every edge case with this pose's own small/reshaped hitbox.
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer actor)) return;
        // A posed player can't act at all - LiePoseEvents' own general
        // right-click-block cancel already covers this too, but checked
        // explicitly here as well since Forge doesn't guarantee ordering
        // between two different classes' same-priority listeners.
        if (LiePoseUtil.isPosed(actor)) return;

        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof BedRestraintItem)) return;

        if (isLookingAtAnotherPlayer(actor)) return; // defer entirely to interactLivingEntity

        InteractionResult result = tryRestrain(stack, actor, actor, null, null);
        if (result == InteractionResult.SUCCESS || result == InteractionResult.FAIL) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
        // PASS (not standing on a bed themselves either): do nothing, let
        // vanilla's own block-use proceed normally (e.g. a real bed's
        // actual sleep attempt, if that's what they were aiming for).
    }

    /**
     * Shared by both entry points. actor == target for self-application.
     * failMessage/conflictMessage are null for the self-apply path (silent
     * PASS-through instead of a message, so a plain "not on a bed" click
     * doesn't spam chat - self-application is opportunistic, not an
     * explicit "restrain THAT player" command the way the entity-click path
     * is) - actor still gets the arm/leg-restraint message either way via a
     * literal fallback below.
     */
    private static InteractionResult tryRestrain(ItemStack stack, ServerPlayer actor, ServerPlayer target,
                                                   @Nullable String notOnBedMessage, @Nullable String conflictMessage) {
        Level level = target.level();

        // Beds have partial-height collision (their top surface sits BELOW
        // a full block's worth of height), so a player standing on one has
        // feet Y that still floors into the BED's OWN block cell, not the
        // cell below it - unlike standing on a full block, where feet Y
        // floors into the cell one above. blockPosition() is tried first
        // (correct for beds), falling back to .below() for full-block-style
        // rounding at an edge.
        BlockPos bedPos = findBedBelow(level, target);
        if (bedPos == null) {
            if (notOnBedMessage != null) {
                actor.displayClientMessage(Component.literal(notOnBedMessage), true);
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        }
        BlockState bedState = level.getBlockState(bedPos);

        Direction facing = bedState.getValue(BedBlock.FACING);
        BedPart part = bedState.getValue(BedBlock.PART);
        // FACING points away from the foot, toward the head - if standing
        // on the head half, the foot half is one block behind, relative to
        // that direction.
        BlockPos footPos = part == BedPart.FOOT ? bedPos : bedPos.relative(facing.getOpposite());

        boolean applied = LiePoseUtil.setPosedOnBed(target, footPos, facing);
        if (!applied) {
            // ROUND 25: [stated] confirmed the standing precondition itself
            // works as intended, but asked for the failure toast to be
            // removed entirely - silently do nothing instead (still refuses
            // the action, just no on-screen message). conflictMessage is
            // now unused dead weight on this path but left as a parameter
            // (still passed by both callers) in case a message is wanted
            // back for a DIFFERENT reason later - simplest to leave the
            // plumbing rather than rip the parameter out and re-add it.
            return InteractionResult.FAIL;
        }

        stack.shrink(1);
        return InteractionResult.SUCCESS;
    }

    /**
     * The bed {@code target} is on, or null if they are not on one.
     *
     * <p>Public wrapper over {@link #findBedBelow} added in 1.5.13 for the
     * dispenser trap ({@code trap.BedRestraintDispenseBehavior}), which has to
     * answer "is this player on a bed" before it decides whether it is even
     * aimed at a valid target. Kept as a wrapper rather than just widening
     * findBedBelow so the two-cell probe stays documented in one place.
     */
    @Nullable
    public static BlockPos bedUnder(Level level, ServerPlayer target) {
        return findBedBelow(level, target);
    }

    /**
     * Apply the Bed Restraint to {@code target} with no acting player at all.
     *
     * <p>Added in 1.5.13 for the dispenser trap. This is
     * {@link #tryRestrain}'s core minus everything that needs an actor: there is
     * no "not on a bed" toast to show and nobody to show it to, and the item is
     * consumed by the caller out of the dispenser rather than out of a hand.
     * The actual restraining is the same single call, so bed geometry and the
     * standing precondition behave identically whether a player or a dispenser
     * triggered it.
     *
     * @return true if the target is now restrained on a bed.
     */
    public static boolean tryRestrainFromTrap(ServerPlayer target) {
        Level level = target.level();
        BlockPos bedPos = findBedBelow(level, target);
        if (bedPos == null) {
            return false;
        }
        BlockState bedState = level.getBlockState(bedPos);
        Direction facing = bedState.getValue(BedBlock.FACING);
        BedPart part = bedState.getValue(BedBlock.PART);
        BlockPos footPos = part == BedPart.FOOT ? bedPos : bedPos.relative(facing.getOpposite());
        return LiePoseUtil.setPosedOnBed(target, footPos, facing);
    }

    @Nullable
    private static BlockPos findBedBelow(Level level, ServerPlayer target) {
        BlockPos own = target.blockPosition();
        BlockState ownState = level.getBlockState(own);
        if (ownState.getBlock().isBed(ownState, level, own, target)) {
            return own;
        }

        BlockPos below = own.below();
        BlockState belowState = level.getBlockState(below);
        if (belowState.getBlock().isBed(belowState, level, below, target)) {
            return below;
        }

        return null;
    }

    private static boolean isLookingAtAnotherPlayer(ServerPlayer actor) {
        Vec3 eyePos = actor.getEyePosition(1.0F);
        Vec3 look = actor.getViewVector(1.0F);
        Vec3 reachPos = eyePos.add(look.scale(SELF_APPLY_LOOK_CHECK_REACH));
        AABB searchBox = actor.getBoundingBox().expandTowards(look.scale(SELF_APPLY_LOOK_CHECK_REACH)).inflate(1.0);

        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                actor, eyePos, reachPos, searchBox,
                e -> e instanceof Player && e != actor && !e.isSpectator(),
                SELF_APPLY_LOOK_CHECK_REACH * SELF_APPLY_LOOK_CHECK_REACH);

        return hit != null;
    }
}
