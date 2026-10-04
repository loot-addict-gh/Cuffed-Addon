package com.example.cuffedaddon.mixin;

import com.example.cuffedaddon.enchantment.IllusionUtil;
import com.lazrproductions.cuffed.entity.animation.HumanoidAnimationHelper;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Half of the leg lock: the half that runs when Cuffed has taken the animation
 * over.
 *
 * <p>Cuffed's {@code HumanoidModelMixin} injects at the HEAD of
 * {@code setupAnim} and <b>cancels it</b> whenever the player has an arm restraint
 * with a tied-arms flag, driving the whole body from
 * {@code animateDefaultHumanoid} instead. A cancelled method never reaches its own
 * RETURN, so {@link HumanoidModelIllusionLegsMixin}'s injection there does not run
 * in that case - and wearing both arm and leg restraints is exactly what someone
 * pretending to be restrained would do.
 *
 * <p>So this covers that path, at the return of the helper Cuffed calls first.
 * Cuffed then overrides the arms and returns; {@code PlayerModel#setupAnim}
 * afterwards copies the legs onto the trouser layer, so the lock carries through
 * to the outer skin for free.
 *
 * <p>Cuffed's own class, so no refmap entry - which is also why the work is split
 * this way round rather than doing everything in one vanilla mixin.
 */
@Mixin(value = HumanoidAnimationHelper.class, remap = false)
public class HumanoidAnimationHelperIllusionLegsMixin {

    @Inject(method = "animateDefaultHumanoid", at = @At("RETURN"), require = 1)
    private static void cuffedaddon$lockIllusionLegs(LivingEntity entity, HumanoidModel<LivingEntity> model,
                                                     float f1, float f2, float f3, float headYRot, float headXRot,
                                                     CallbackInfo ci) {
        if (IllusionUtil.hasIllusionLegsClientSide(entity)) {
            IllusionUtil.lockLegs(model);
        }
    }
}
