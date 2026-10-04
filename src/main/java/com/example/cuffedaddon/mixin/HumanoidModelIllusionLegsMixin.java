package com.example.cuffedaddon.mixin;

import com.example.cuffedaddon.enchantment.IllusionUtil;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The other half of the leg lock: the half that runs when vanilla animated the
 * body normally.
 *
 * <p>When the player has a leg restraint but NO arm restraint, Cuffed's own
 * {@code setupAnim} mixin does not cancel anything, so vanilla runs and swings the
 * legs with the walk cycle. Injecting at the return of vanilla's own
 * {@code setupAnim} overwrites that afterwards. The companion
 * {@link HumanoidAnimationHelperIllusionLegsMixin} covers the case where Cuffed
 * cancelled and this never runs.
 *
 * <p>Placed on {@code HumanoidModel} rather than {@code PlayerModel} on purpose:
 * {@code PlayerModel#setupAnim} calls {@code super.setupAnim} and then copies the
 * legs onto the trouser layer, so locking at the supertype's return means the
 * trousers inherit the locked pose instead of needing a second fix.
 *
 * <p>The model parts are reached by casting {@code this}, which is ordinary
 * bytecode that ForgeGradle remaps like any other code - the same thing Cuffed's
 * own model mixin does - rather than {@code @Shadow}, which would put seven more
 * hand-written field names into the refmap. <b>The method name still needs a
 * refmap entry</b> ({@code setupAnim} is vanilla and SRG-obfuscated in
 * production); it is in mixins.cuffedaddon.refmap.json under this class.
 */
@Mixin(HumanoidModel.class)
public class HumanoidModelIllusionLegsMixin {

    @Inject(method = "setupAnim", at = @At("RETURN"), require = 1)
    private void cuffedaddon$lockIllusionLegs(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                              float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (IllusionUtil.hasIllusionLegsClientSide(entity)) {
            IllusionUtil.lockLegs((HumanoidModel<?>) (Object) this);
        }
    }
}
