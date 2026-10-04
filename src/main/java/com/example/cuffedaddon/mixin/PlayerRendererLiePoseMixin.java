package com.example.cuffedaddon.mixin;

import com.example.cuffedaddon.pose.LiePoseUtil;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ROUND (prior session): first re-attempt at the "crouch link" issue,
 * targeting PlayerRenderer#getRenderOffset - see that round's own
 * commentary in git history/chat for the full reasoning. [stated] tested
 * this specific fix and confirmed the bug was STILL present afterward.
 *
 * ROUND (this session): with that ruled out, [stated] gave much more
 * precise symptom detail - the sinking/shifting is visible to EVERY
 * viewer consistently, including the restrained player's own client, not
 * just the actor's. That pointed at a per-frame MODEL state bug rather
 * than a render-offset (translation-of-the-whole-model) one - see
 * HumanoidModelLiePoseMixin's own new commentary for the actual
 * root cause found (stale ModelPart x/y/z translation fields left over
 * from a shared model instance rendering a crouching entity moments
 * earlier, never reset because our own setupAnim override only touched
 * rotations) and its fix.
 *
 * Kept in place regardless (harmless, still a real per-entity offset that
 * could theoretically matter for something else that reads render
 * offset), but the HumanoidModelLiePoseMixin fix is the one actually
 * expected to resolve the reported symptom this time.
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererLiePoseMixin {

    @Inject(
            method = "getRenderOffset",
            at = @At("HEAD"),
            cancellable = true
    )
    private void cuffedaddon$zeroOffsetIfPosed(AbstractClientPlayer entity, float partialTicks,
                                                 CallbackInfoReturnable<Vec3> cir) {
        if (LiePoseUtil.isPosed(entity)) {
            cir.setReturnValue(Vec3.ZERO);
        }
    }
}
