package com.example.cuffedaddon.mixin;

import com.example.cuffedaddon.pose.LiePoseUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reproduces, directly at render time, the exact transform vanilla's own
 * LivingEntityRenderer#setupRotations applies for Pose.SLEEPING - see
 * LiePoseUtil.applyRenderRotationIfPosed for the actual rotation math.
 *
 * gated ENTIRELY on this addon's own ILiePose capability instead of Pose,
 * cancelling the rest of the method so vanilla's normal upright rotation
 * never also runs. This never touches Pose anywhere at all - see
 * LiePoseEvents' class doc / /areas/cuffedaddon.md for why (the old Bed
 * Restraint tried forcing Pose.SLEEPING every tick and it caused camera
 * jitter fighting Player#aiStep()'s own updatePlayerPose()).
 *
 * setupRotations' bare name + refmap SRG mapping (m_7523_) are copied
 * verbatim from that same confirmed-working entry in mixins.cuffedaddon.refmap.json.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererLiePoseMixin<T extends LivingEntity, M extends EntityModel<T>> {

    @Inject(
            method = "setupRotations",
            at = @At("HEAD"),
            cancellable = true
    )
    private void cuffedaddon$applyLiePoseLieFlat(T entity, PoseStack poseStack, float ageInTicks,
                                                   float rotationYaw, float partialTicks, CallbackInfo ci) {
        if (LiePoseUtil.applyRenderRotationIfPosed(entity, poseStack)) {
            ci.cancel();
        }
    }
}
