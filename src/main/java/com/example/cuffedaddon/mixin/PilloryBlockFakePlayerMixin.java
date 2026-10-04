package com.example.cuffedaddon.mixin;

import com.example.cuffedaddon.fakeplayer.FakePlayerSupport;
import com.example.cuffedaddon.fakeplayer.FakeStationaryUtil;
import com.lazrproductions.cuffed.blocks.PilloryBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets Cuffed's pillory catch a Fake Players fake player standing in front of it,
 * exactly as it catches a real player.
 *
 * <h2>Why a mixin, and why this method</h2>
 * {@code PilloryBlock#use} already does everything that does not depend on WHO is
 * being locked up: it checks the clicker, forwards a click on the lower half to the
 * upper half, plays {@code cuffed:block.pillory.use}, and writes the {@code CLOSED}
 * block state from the return value of one method -
 * {@code attemptToToggleDetained}. That method is the single place the occupant is
 * looked up, and it looks the occupant up as a {@code Player}:
 * <pre>
 *   Player p = level.getNearestPlayer(TargetingConditions.forNonCombat(), behind.x, behind.y, behind.z);
 * </pre>
 * Injecting at its head is therefore the whole feature: sound, block state,
 * lower/upper forwarding and the clicker's own checks all keep working untouched,
 * and only "who is standing there" is extended. That is the same
 * "find the single narrow dispatch point upstream" approach that made one small
 * mixin cover every Cuffed restraint overlay in 1.3.x, rather than mixing into
 * each leaf.
 *
 * <h2>No refmap entry needed</h2>
 * This targets Cuffed's OWN class, and mods are not obfuscated in production Forge
 * - only vanilla classes are. Hence {@code remap = false} throughout and nothing to
 * add to {@code mixins.cuffedaddon.refmap.json}, which is hand-maintained in this
 * project.
 *
 * <h2>Real players keep absolute priority</h2>
 * If Cuffed's own lookup would find a real player at that spot, this injection
 * returns without cancelling and Cuffed handles the click exactly as before. Only
 * when no real player qualifies does a fake player get considered. Two entities
 * cannot both be within 0.3 blocks of the same point in any practical sense, so
 * this is really only about never changing the existing behaviour when in doubt.
 *
 * <h2>The toggle logic is Cuffed's, restated</h2>
 * {@code attemptToToggleDetained} returns whether the block should end up OPEN.
 * Cuffed's own three outcomes, mirrored here for a fake player:
 * <ul>
 *   <li>block closed, occupant detained -&gt; release, return true (open);</li>
 *   <li>block open, occupant free and lockable -&gt; detain, return false (closed);</li>
 *   <li>anything else -&gt; leave it, which is Cuffed's own {@code !wasOpen}.</li>
 * </ul>
 */
@Mixin(value = PilloryBlock.class, remap = false)
public abstract class PilloryBlockFakePlayerMixin {

    @Inject(method = "attemptToToggleDetained", at = @At("HEAD"), cancellable = true, remap = false)
    private void cuffedaddon$toggleFakePlayer(Level level, boolean wasOpen, BlockState state, BlockPos pos,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (!FakePlayerSupport.isModLoaded() || level.isClientSide()) {
            return;
        }
        // Cuffed only ever acts from the UPPER half (the lower half's own use()
        // forwards there first), so anything else is not a decision point at all.
        if (state.getValue(PilloryBlock.HALF) != DoubleBlockHalf.UPPER) {
            return;
        }

        Vec3 behind = PilloryBlock.getPositionBehind(state, pos);
        if (realPlayerAt(level, behind)) {
            return; // Cuffed's own path owns this click
        }

        LivingEntity fake = FakeStationaryUtil.findFakePlayerBehindPillory(level, behind);
        if (fake == null) {
            return;
        }

        if (!wasOpen) {
            if (FakeStationaryUtil.undetain(fake)) {
                cir.setReturnValue(true);
            }
            return;
        }

        float facingYaw = state.getValue(PilloryBlock.FACING).toYRot();
        boolean detained = FakeStationaryUtil.tryDetain(fake, pos, behind, facingYaw);
        FakeStationaryUtil.reportFirstApply("pillory", detained);
        // Leave it open if it could not be locked - Cuffed's own else branch.
        cir.setReturnValue(!detained);
    }

    /**
     * Whether Cuffed's own lookup would find a real player at this point.
     *
     * <p>Deliberately NOT {@code level.getNearestPlayer(TargetingConditions...)},
     * which is what Cuffed calls: the point here is only to decide whether to yield,
     * and an AABB query plus Cuffed's own {@code dist < 0.3f} horizontal test gives
     * the same answer using API this addon already uses everywhere else. It errs
     * toward yielding, which is the safe direction.
     */
    private static boolean realPlayerAt(Level level, Vec3 behind) {
        AABB box = new AABB(behind.x - 1.5, behind.y - 1.5, behind.z - 1.5,
                behind.x + 1.5, behind.y + 1.5, behind.z + 1.5);
        for (Player candidate : level.getEntitiesOfClass(Player.class, box)) {
            double dx = behind.x - candidate.position().x;
            double dz = behind.z - candidate.position().z;
            if (Math.sqrt(dx * dx + dz * dz) < 0.3) {
                return true;
            }
        }
        return false;
    }
}
