package com.example.cuffedaddon.mixin;

import com.example.cuffedaddon.pose.LiePoseUtil;
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
 * Injects at the head of setupAnim and, if the entity is lie-posed, hard-sets
 * every body part's rotation to a fixed "spread-eagle" pose (legs spread to
 * the sides, arms up and outward) and cancels the vanilla method entirely -
 * the same technique the old Bed Restraint's HumanoidModelBedPoseMixin used.
 *
 * The angles below are chosen AS IF POSING A STANDING CHARACTER (this mixin
 * only ever runs before LivingEntityRendererLiePoseMixin's whole-body
 * rotation is applied, and that rotation is rigid - it preserves the
 * relative angles between body parts). A standing "arms raised up and
 * outward, legs spread to the sides" pose (like the top of a jumping jack)
 * becomes exactly that same shape once the whole model is laid flat and
 * viewed from above - confirmed by the same reasoning that made the
 * body-direction fix work (see /areas/cuffedaddon.md, "Lie Pose foundation"
 * round 4). This is what makes it possible to reason about limb angles here
 * at all without needing to re-derive the lying-flat geometry from scratch.
 *
 * ARM_SPREAD_DEGREES/LEG_SPREAD_DEGREES are still best-effort angles, not
 * confirmed exact - the SIGN convention (which side gets + vs - zRot) WAS
 * wrong in the first pass (round 5: both arms/legs mirrored to the wrong
 * side) and has been flipped (round 6) - CONFIRMED correct side as of
 * round 14 testing (handcuffs + head tilt both confirmed working, which
 * only render correctly if the limb pose itself is basically right).
 * Round 14 reduced both angles a step for being too spread; round 15
 * pushed ARM_SPREAD_DEGREES back up to 160 per explicit request (legs
 * stayed at round 14's value, not reported as an issue). At 160,
 * [stated] saw the posed player's model visibly twitch. NOT YET FIXED -
 * best available hypothesis (not confirmed): at this angle vanilla's
 * standard arm pivot (offset ~5/16 block from center) swings the arm tip
 * up near/right at the head cuboid's edge, and since the head's own
 * rotation keeps updating every frame (live look-tracking, see below),
 * the two cuboids' overlap could flicker in and out each frame - would
 * predict the twitching tracks the RESTRAINED player's own look
 * direction. Left untouched rather than guessing a geometry offset that
 * can't be verified without rendering - flag this back with whether it
 * correlates with the target's own look movement, and this can be fixed
 * precisely next round.
 *
 * This mixin only handles LIMB angles. Whole-body "lying flat" orientation
 * is handled separately by LivingEntityRendererLiePoseMixin.
 *
 * The seven body-part fields are fetched via REFLECTION (SRG names first,
 * falling back to official/dev-mapped names), NOT @Shadow - carried over
 * verbatim from the Bed Restraint line's HumanoidModelBedPoseMixin, where
 * @Shadow field resolution through the refmap kept failing despite correct
 * SRG names; reflection only needs the name to be right. Only setupAnim
 * itself still goes through @Inject/refmap (m_6973_, copied from Cuffed's
 * own working refmap entry, reused verbatim here).
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelLiePoseMixin<T extends LivingEntity> {

    private static final Logger LOGGER = LoggerFactory.getLogger("cuffedaddon/liepose");

    // Degrees, converted to radians below. 0 = hanging straight down
    // (vanilla rest), 90 = straight out to the side, 180 = straight up.
    // ARM_SPREAD sits past 90 (up AND outward, like a jumping jack's top
    // position / referee "touchdown" signal) to match the "arms up and
    // outward" ask; LEG_SPREAD sits well short of 90 (just spread apart,
    // not raised) to match "legs spread to the sides".
    private static final float ARM_SPREAD_DEGREES = 160.0F;
    private static final float LEG_SPREAD_DEGREES = 25.0F;
    private static final float ARM_SPREAD_RAD = ARM_SPREAD_DEGREES * ((float) Math.PI / 180F);

    // ROUND (this session): [stated] asked for the head to stop tracking
    // the restrained player's own look direction entirely - with live
    // head-follow layered on top of this fixed lift, the head visibly
    // swam around as the target moved their mouse, which read as "way too
    // weird" for someone lying restrained on a bed. The head is now a pure
    // fixed offset from the body's own rest orientation (0,0) - see
    // cuffedaddon$applyLiePose below, which no longer reads netHeadYaw/
    // headPitch at all for the head/hat fields.
    //
    // How far the head tilts forward off that fixed rest orientation, as
    // if lifted slightly off the ground (a natural "look at your own feet"
    // head-lift for someone on their back) - requested as a fixed 45
    // degrees.
    //
    // SIGN IS A BEST-EFFORT GUESS, NOT CONFIRMED: xRot's positive direction
    // for the head (does it tilt the chin toward the chest, i.e. "look
    // down" in the normal standing convention?) combined with how that then
    // reads once the whole body is lying flat is exactly the same kind of
    // compound-rotation question that took real in-game screenshots to pin
    // down for the body's own facing direction (see /areas/cuffedaddon.md,
    // "Lie Pose foundation") - not something derivable with certainty here.
    // If the head tilts backward instead of forward, flip this to -45.0F.
    private static final float HEAD_LIFT_DEGREES = 45.0F;
    private static final float HEAD_LIFT_RAD = HEAD_LIFT_DEGREES * ((float) Math.PI / 180F);
    private static final float LEG_SPREAD_RAD = LEG_SPREAD_DEGREES * ((float) Math.PI / 180F);

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
            // Real installed (production) Forge: fields are SRG-obfuscated.
            head = HumanoidModel.class.getDeclaredField("f_102808_");
            hat = HumanoidModel.class.getDeclaredField("f_102809_");
            body = HumanoidModel.class.getDeclaredField("f_102810_");
            rightArm = HumanoidModel.class.getDeclaredField("f_102811_");
            leftArm = HumanoidModel.class.getDeclaredField("f_102812_");
            rightLeg = HumanoidModel.class.getDeclaredField("f_102813_");
            leftLeg = HumanoidModel.class.getDeclaredField("f_102814_");
        } catch (NoSuchFieldException srgFailed) {
            try {
                // Gradle dev run (runClient/runServer): fields use official names.
                head = HumanoidModel.class.getDeclaredField("head");
                hat = HumanoidModel.class.getDeclaredField("hat");
                body = HumanoidModel.class.getDeclaredField("body");
                rightArm = HumanoidModel.class.getDeclaredField("rightArm");
                leftArm = HumanoidModel.class.getDeclaredField("leftArm");
                rightLeg = HumanoidModel.class.getDeclaredField("rightLeg");
                leftLeg = HumanoidModel.class.getDeclaredField("leftLeg");
            } catch (NoSuchFieldException officialFailed) {
                LOGGER.error("[cuffedaddon] Could not resolve HumanoidModel body part fields via reflection "
                        + "under either SRG or official names - lie pose will not apply.", officialFailed);
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

    @Inject(
            method = "setupAnim",
            at = @At("HEAD"),
            cancellable = true
    )
    private void cuffedaddon$applyLiePose(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                            float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        // LivingEntity, not Player - 1.5.11. Widened so a bed-restrained Fake
        // Players fake player gets this exact limb pose rather than a second,
        // drifting copy of it; see fakeplayer/FakeStationaryUtil. Nothing else
        // changes: the gate is still this addon's own ILiePose capability, which is
        // only ever set on a player or on one of their entities.
        if (!LiePoseUtil.isPosed(entity)) {
            return;
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
            return; // reflection failed at class-init - already logged once above, don't crash the render loop
        }

        // ROUND (this session): [stated] confirmed the "crouch link" is
        // NOT a network/observer artifact - it's visible to every viewer
        // consistently, INCLUDING the restrained player's own client, and
        // tracks whenever ANY nearby player crouches. That ruled out the
        // previously-tried getRenderOffset fix's whole premise (a
        // per-frame render-offset TRANSLATION of the whole model, which
        // would at most explain a Y shift, not "legs sinking, upper body
        // shifting" as separately-described symptoms).
        //
        // ACTUAL ROOT CAUSE, found by re-reading what we ourselves DON'T
        // touch here: HumanoidModel/PlayerModel is ONE SHARED instance,
        // reused sequentially to render every player entity each frame -
        // and vanilla's real (uncancelled) setupAnim doesn't just set each
        // part's xRot/yRot/zRot, it ALSO sets each part's x/y/z TRANSLATION
        // fields every single frame (0 normally; nonzero for a crouching
        // entity - vanilla bends+drops the body/head/arms/legs by
        // translating them, not just rotating). Our @Inject cancels the
        // ENTIRE method before any of that runs for a posed entity, and we
        // were only ever overwriting each part's ROTATION fields - never
        // their translation fields. So if this SAME shared model instance
        // rendered a crouching player (the actor, or anyone else nearby)
        // on an earlier call this frame, its translation fields are left
        // exactly as that crouching render left them, and our posed
        // entity's parts inherit those stale offsets on top of our own
        // rotation-only pose - explaining the sinking/shifting precisely,
        // deterministically, for every viewer (including the posed
        // player's own third-person view of themselves), with no timing/
        // network component at all.
        //
        // Fixed by resetting every part via ModelPart#resetPose() (a real
        // vanilla method, confirmed via mappings.dev - restores x/y/z AND
        // xRot/yRot/zRot back to that part's baked PartPose from when the
        // model was built, NOT to literal 0) alongside the rotations we
        // still set explicitly below, so nothing can leak in from whatever
        // this shared model instance was used for a moment earlier.
        //
        // CORRECTION, this round: the first attempt at this fix hardcoded
        // x/y/z to 0.0F directly instead of calling resetPose() - WRONG,
        // confirmed by [stated]'s screenshot: arms and legs are offset
        // from the torso centerline in their baked PartPose (that's what
        // keeps them from sitting inside the body at all), so forcing
        // those fields to literal 0 collapsed every limb back onto the
        // torso's own center, independent of whatever rotation was also
        // applied - "correct rotations, wrong starting positions" is
        // exactly what a 0.0F pivot on an off-center part looks like.
        // resetPose() restores the REAL baked pivot instead of a made-up
        // zero, which still clears any stale crouch-translate leak just
        // as well (that's a delta FROM the real pivot, not from zero).
        head.resetPose();
        hat.resetPose();
        body.resetPose();
        rightArm.resetPose();
        leftArm.resetPose();
        rightLeg.resetPose();
        leftLeg.resetPose();

        // Head is now FIXED, not look-tracking: [stated] asked for the
        // head to stay put (with the 45-degree lift baked in) instead of
        // swiveling around with the restrained player's own mouse look -
        // netHeadYaw/headPitch (the params that used to drive this) are
        // deliberately unused now. Pivots from the head's own existing
        // pivot point (the neck/seam - no extra pivot math needed,
        // ModelPart rotations always apply around their own defined
        // origin).
        head.yRot = 0.0F;
        head.xRot = HEAD_LIFT_RAD;
        hat.yRot = head.yRot;
        hat.xRot = head.xRot;
        hat.zRot = head.zRot;

        body.xRot = 0.0F;
        body.yRot = 0.0F;
        body.zRot = 0.0F;

        // Arms up and outward (zRot swings a hanging arm sideways; past 90
        // degrees it continues up-and-in past horizontal, giving the
        // diagonal "raised V" look) - right/left get opposite signs so they
        // spread apart from each other rather than both swinging the same
        // way. CONFIRMED (round 6): the original guess had both sides
        // mirrored to the wrong side - signs flipped from round 5.
        rightArm.xRot = 0.0F;
        rightArm.yRot = 0.0F;
        rightArm.zRot = ARM_SPREAD_RAD;

        leftArm.xRot = 0.0F;
        leftArm.yRot = 0.0F;
        leftArm.zRot = -ARM_SPREAD_RAD;

        // Legs spread to the sides - same idea, smaller angle (just spread
        // apart, not raised). Signs flipped from round 5, same as arms.
        rightLeg.xRot = 0.0F;
        rightLeg.yRot = 0.0F;
        rightLeg.zRot = LEG_SPREAD_RAD;

        leftLeg.xRot = 0.0F;
        leftLeg.yRot = 0.0F;
        leftLeg.zRot = -LEG_SPREAD_RAD;

        ci.cancel();
    }
}
