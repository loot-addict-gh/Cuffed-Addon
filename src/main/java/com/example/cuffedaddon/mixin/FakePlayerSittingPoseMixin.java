package com.example.cuffedaddon.mixin;

import com.example.cuffedaddon.client.model.PlayerModelParts;
import com.example.cuffedaddon.fakeplayer.ISittingRestraintFlags;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes Fake Players' own SITTING pose respect Cuffed restraints - [stated]'s
 * request at 1.5.11: <i>"since i want to keep poses with the 3 slot restraints, can
 * you look into the sitting pose and make that be affected by the restraints?
 * specifically, arms should be in the back with arm restraints and legs should be
 * closed with leg restraints"</i>.
 *
 * <h2>The problem this fixes</h2>
 * Their model poses a sitting fake player AFTER everything this addon does, and it
 * does not blend:
 * <pre>
 *   public void setupAnim(FakePlayerEntity entity, ...) {
 *       super.setupAnim((LivingEntity) entity, ...);   // -&gt; our tied-arms pose lands here
 *       if (entity.isSitting() &amp;&amp; !this.young) this.translateSitting();
 *   }
 *
 *   private void translateSitting() {
 *       rightArm.xRot += -0.62831855F;  rightSleeve.xRot += -0.62831855F;
 *       leftArm.xRot  += -0.62831855F;  leftSleeve.xRot  += -0.62831855F;
 *       rightLeg.xRot = -1.4137167F; rightLeg.yRot =  0.31415927F; rightLeg.zRot =  0.07853982F;
 *       leftLeg.xRot  = -1.4137167F; leftLeg.yRot  = -0.31415927F; leftLeg.zRot  = -0.07853982F;
 *       ... and the same on rightPants / leftPants
 *   }
 * </pre>
 * The arms are ADDED to, so a tied pose gets swung forward out of place; the legs are
 * HARD-SET, so a leg restraint has no visible effect at all while sitting.
 *
 * <h2>Why it replaces the method rather than correcting it afterwards</h2>
 * The arms could have been pre-compensated (add {@code +0.62831855F} before, let
 * their code subtract it) - but the legs are assigned, not accumulated, so there is
 * nothing to pre-compensate. One method that owns the whole sitting pose is simpler
 * to reason about than a pre-pass plus a post-pass, and it keeps the numbers in one
 * place where they can be checked against the decompile above.
 *
 * <p><b>When nothing is restrained this cancels nothing and their own code runs
 * untouched</b>, so an ordinary sitting fake player is bit-for-bit as before.
 *
 * <h2>Why the values are transcribed rather than derived</h2>
 * They are Fake Players' own numbers, read from a CFR decompile of 2.2.0. The point
 * is for a restrained sitting fake player to sit EXACTLY as it otherwise would,
 * differing only in the two things [stated] asked for. Re-deriving "a good sitting
 * angle" would have changed the pose for its own sake.
 *
 * <p>The only new behaviour is: tied arms keep the pose Cuffed's own animation helper
 * gave them (so "arms in the back" stays in the back), and bound legs lose the
 * outward splay while keeping the seated thigh angle.
 */
@Mixin(targets = "dev.duzo.players.client.model.FakePlayerModel", remap = false)
public abstract class FakePlayerSittingPoseMixin {

    /** Their own sitting constants - see the class doc. */
    private static final float SIT_ARM_X_DELTA = -0.62831855F;
    private static final float SIT_LEG_X_ROT = -1.4137167F;
    private static final float SIT_LEG_Y_ROT = 0.31415927F;
    private static final float SIT_LEG_Z_ROT = 0.07853982F;

    @Inject(method = "translateSitting", at = @At("HEAD"), cancellable = true, remap = false)
    private void cuffedaddon$restraintAwareSitting(CallbackInfo ci) {
        if (!(this instanceof ISittingRestraintFlags holder)) {
            return; // FakePlayerArmPoseMixin did not apply - leave their pose alone
        }
        int flags = holder.cuffedaddon$sittingRestraintFlags();
        if (flags == 0) {
            return; // nothing restrained: their own translateSitting runs, untouched
        }

        ModelPart rightArm = PlayerModelParts.rightArm(this);
        ModelPart leftArm = PlayerModelParts.leftArm(this);
        ModelPart rightLeg = PlayerModelParts.rightLeg(this);
        ModelPart leftLeg = PlayerModelParts.leftLeg(this);
        if (rightArm == null || leftArm == null || rightLeg == null || leftLeg == null) {
            return; // reflection failed at class-init, already logged once - do not break the render
        }

        boolean armsTied = (flags & ISittingRestraintFlags.ARMS_TIED) != 0;
        boolean legsBound = (flags & ISittingRestraintFlags.LEGS_BOUND) != 0;

        // ARMS. Tied arms keep exactly the pose Cuffed's HumanoidAnimationHelper just
        // gave them - skipping the seated swing is the whole fix, since that swing is
        // what was dragging bound wrists out in front of the body. Free arms get their
        // own code's delta, unchanged.
        if (!armsTied) {
            rightArm.xRot += SIT_ARM_X_DELTA;
            leftArm.xRot += SIT_ARM_X_DELTA;
        }
        PlayerModelParts.copyRotation(rightArm, PlayerModelParts.rightSleeve(this));
        PlayerModelParts.copyRotation(leftArm, PlayerModelParts.leftSleeve(this));

        // LEGS. The seated thigh angle (xRot) is kept either way - a restrained fake
        // player is still sitting. Only the outward splay goes, which is what "closed"
        // means here: knees and ankles together instead of turned out.
        float splayY = legsBound ? 0.0F : SIT_LEG_Y_ROT;
        float splayZ = legsBound ? 0.0F : SIT_LEG_Z_ROT;

        rightLeg.xRot = SIT_LEG_X_ROT;
        rightLeg.yRot = splayY;
        rightLeg.zRot = splayZ;

        leftLeg.xRot = SIT_LEG_X_ROT;
        leftLeg.yRot = -splayY;
        leftLeg.zRot = -splayZ;

        PlayerModelParts.copyRotation(rightLeg, PlayerModelParts.rightPants(this));
        PlayerModelParts.copyRotation(leftLeg, PlayerModelParts.leftPants(this));

        ci.cancel();
    }
}
