package com.example.cuffedaddon.mixin;

import com.example.cuffedaddon.pose.WallPoseUtil;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * Injects at the head of setupAnim and, if the entity is wall-posed,
 * hard-sets a fixed "spread-eagle" limb pose (legs spread to the sides,
 * arms up and outward) and cancels the vanilla method - same technique and
 * same reflection-based field lookup as cuffedaddon's
 * HumanoidModelLiePoseMixin (SRG names first, official/dev names as
 * fallback - see that class's own doc for why reflection instead of
 * @Shadow).
 *
 * The ARM_SPREAD_DEGREES/LEG_SPREAD_DEGREES values and sign convention are
 * copied VERBATIM from cuffedaddon's confirmed-working values - that mixin
 * was written "as if posing a standing character" and only ever laid flat
 * afterward by a SEPARATE whole-body rotation (LivingEntityRendererLiePoseMixin).
 * This pose deliberately has NO equivalent of that second rotation - the
 * wall-posed player stays genuinely standing - so reusing the exact same
 * limb angles here, with nothing extra applied on top, is expected to give
 * the same visual "arms up and outward, legs spread" shape directly,
 * without needing to re-derive or re-tune them. Flag if it looks different
 * once tested (an angle tuned for "how it reads once laid flat" might not
 * look identical "as read standing up", even though the underlying rotation
 * values are identical).
 *
 * The head is NOT fixed here (unlike Bed Restraint's HEAD_LIFT_DEGREES) -
 * it uses the actual netHeadYaw/headPitch parameters, the same formula
 * vanilla's own setupAnim uses, so a wall-posed player's head still turns
 * to track where they're looking (within the 180-degree look cone - see
 * ClientWallPoseEvents), which reads naturally for someone standing upright
 * and conscious, unlike someone lying restrained on a bed.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelWallPoseMixin<T extends LivingEntity> {

    private static final Logger LOGGER = LoggerFactory.getLogger("cuffedaddon/wallpose");

    private static final float ARM_SPREAD_DEGREES = 160.0F;
    private static final float LEG_SPREAD_DEGREES = 25.0F;
    private static final float ARM_SPREAD_RAD = ARM_SPREAD_DEGREES * ((float) Math.PI / 180F);
    private static final float LEG_SPREAD_RAD = LEG_SPREAD_DEGREES * ((float) Math.PI / 180F);
    private static final float DEG_TO_RAD = (float) Math.PI / 180F;

    private static final Field HEAD_FIELD;
    private static final Field HAT_FIELD;
    private static final Field BODY_FIELD;
    private static final Field RIGHT_ARM_FIELD;
    private static final Field LEFT_ARM_FIELD;
    private static final Field RIGHT_LEG_FIELD;
    private static final Field LEFT_LEG_FIELD;

    static {
        Field head, hat, body, rightArm, leftArm, rightLeg, leftLeg;
        try {
            head = HumanoidModel.class.getDeclaredField("f_102808_");
            hat = HumanoidModel.class.getDeclaredField("f_102809_");
            body = HumanoidModel.class.getDeclaredField("f_102810_");
            rightArm = HumanoidModel.class.getDeclaredField("f_102811_");
            leftArm = HumanoidModel.class.getDeclaredField("f_102812_");
            rightLeg = HumanoidModel.class.getDeclaredField("f_102813_");
            leftLeg = HumanoidModel.class.getDeclaredField("f_102814_");
        } catch (NoSuchFieldException srgFailed) {
            try {
                head = HumanoidModel.class.getDeclaredField("head");
                hat = HumanoidModel.class.getDeclaredField("hat");
                body = HumanoidModel.class.getDeclaredField("body");
                rightArm = HumanoidModel.class.getDeclaredField("rightArm");
                leftArm = HumanoidModel.class.getDeclaredField("leftArm");
                rightLeg = HumanoidModel.class.getDeclaredField("rightLeg");
                leftLeg = HumanoidModel.class.getDeclaredField("leftLeg");
            } catch (NoSuchFieldException officialFailed) {
                LOGGER.error("Could not resolve HumanoidModel body part fields via reflection "
                        + "under either SRG or official names - wall pose will not apply.", officialFailed);
                head = hat = body = rightArm = leftArm = rightLeg = leftLeg = null;
            }
        }
        HEAD_FIELD = head;
        HAT_FIELD = hat;
        BODY_FIELD = body;
        RIGHT_ARM_FIELD = rightArm;
        LEFT_ARM_FIELD = leftArm;
        RIGHT_LEG_FIELD = rightLeg;
        LEFT_LEG_FIELD = leftLeg;
        for (Field f : new Field[]{HEAD_FIELD, HAT_FIELD, BODY_FIELD, RIGHT_ARM_FIELD, LEFT_ARM_FIELD, RIGHT_LEG_FIELD, LEFT_LEG_FIELD}) {
            if (f != null) f.setAccessible(true);
        }
    }

    private static ModelPart get(Field field, Object model) {
        try {
            return field != null ? (ModelPart) field.get(model) : null;
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private static boolean loggedFirstApply = false;

    @Inject(
            method = "setupAnim",
            at = @At("HEAD"),
            cancellable = true
    )
    private void cuffedaddon$applyWallPose(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                              float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        // LivingEntity, not Player - 1.5.11, same widening as the lie-pose mixin
        // so a wall-restrained fake player shares this pose. See
        // fakeplayer/FakeStationaryUtil.
        if (!WallPoseUtil.isPosed(entity)) {
            return;
        }

        // ONE-TIME diagnostic (search the log for "WALLPOSE-MIXIN-FIRED"):
        // confirms this mixin is actually applying, same purpose as the
        // "crouch link" investigation this class's own doc references -
        // cheap to leave in permanently for future debugging.
        if (!loggedFirstApply) {
            LOGGER.info("WALLPOSE-MIXIN-FIRED - HumanoidModelWallPoseMixin is applying the wall pose.");
            loggedFirstApply = true;
        }

        ModelPart head = get(HEAD_FIELD, this);
        ModelPart hat = get(HAT_FIELD, this);
        ModelPart body = get(BODY_FIELD, this);
        ModelPart rightArm = get(RIGHT_ARM_FIELD, this);
        ModelPart leftArm = get(LEFT_ARM_FIELD, this);
        ModelPart rightLeg = get(RIGHT_LEG_FIELD, this);
        ModelPart leftLeg = get(LEFT_LEG_FIELD, this);
        if (head == null || hat == null || body == null || rightArm == null
                || leftArm == null || rightLeg == null || leftLeg == null) {
            return;
        }

        // Same shared-model-instance stale-transform hazard cuffedaddon's
        // own mixin found and fixed (see its class doc, "crouch link") -
        // reset every part before setting our own rotations so nothing
        // leaks in from whatever this shared PlayerModel instance rendered
        // a moment earlier.
        head.resetPose();
        hat.resetPose();
        body.resetPose();
        rightArm.resetPose();
        leftArm.resetPose();
        rightLeg.resetPose();
        leftLeg.resetPose();

        // Live head tracking (vanilla's own formula) - see class doc for why
        // this pose keeps it, unlike Bed Restraint's fixed head lift.
        head.yRot = netHeadYaw * DEG_TO_RAD;
        head.xRot = headPitch * DEG_TO_RAD;
        hat.yRot = head.yRot;
        hat.xRot = head.xRot;
        hat.zRot = head.zRot;

        body.xRot = 0.0F;
        body.yRot = 0.0F;
        body.zRot = 0.0F;

        rightArm.xRot = 0.0F;
        rightArm.yRot = 0.0F;
        rightArm.zRot = ARM_SPREAD_RAD;

        leftArm.xRot = 0.0F;
        leftArm.yRot = 0.0F;
        leftArm.zRot = -ARM_SPREAD_RAD;

        rightLeg.xRot = 0.0F;
        rightLeg.yRot = 0.0F;
        rightLeg.zRot = LEG_SPREAD_RAD;

        leftLeg.xRot = 0.0F;
        leftLeg.yRot = 0.0F;
        leftLeg.zRot = -LEG_SPREAD_RAD;

        ci.cancel();
    }
}
