package com.example.cuffedaddon.mixin;

import com.example.cuffedaddon.CuffedAddon;
import com.example.cuffedaddon.fakeplayer.FakePlayerSupport;
import com.example.cuffedaddon.fakeplayer.FakeStationaryUtil;
import com.example.cuffedaddon.fakeplayer.IFakeDetained;
import com.example.cuffedaddon.fakeplayer.ISittingRestraintFlags;
import com.lazrproductions.cuffed.entity.animation.ArmRestraintAnimationFlags;
import com.lazrproductions.cuffed.entity.animation.HumanoidAnimationHelper;
import com.lazrproductions.cuffed.entity.base.IRestrainableEntity;
import com.lazrproductions.cuffed.restraints.RestraintAPI;
import com.lazrproductions.cuffed.restraints.base.RestraintType;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives a restrained fake player the same bound-arms pose a restrained real
 * player gets.
 *
 * <h2>Why a second mixin on a method Cuffed already mixes into</h2>
 * Cuffed's own {@code HumanoidModelMixin} does exactly this job at the head of
 * the same method, and it is the right implementation - it is simply gated:
 * <pre>
 *   if (entity instanceof Player p) { ... }
 * </pre>
 * A fake player is a {@code PathfinderMob}, so it never enters that branch. This
 * mixin is that branch's missing twin: same injection point, same helper calls,
 * same cancel, but keyed on a fake player and reading the arm restraint out of
 * this addon's capability instead of Cuffed's player capability. Nothing is
 * re-derived - {@link HumanoidAnimationHelper#animateArmsTiedFront} and
 * {@link HumanoidAnimationHelper#animateArmsTiedBack} are Cuffed's own, so the
 * pose is identical by construction rather than by eye, crouch variants included.
 *
 * <h2>Why this reaches the model at all</h2>
 * It is worth recording, because the opposite assumption cost 1.5.0 and 1.5.1.
 * Fake Players' model overrides {@code setupAnim}, and an override that did not
 * call up would have made this injection point unreachable. It does call up:
 * <pre>
 *   public void setupAnim(FakePlayerEntity entity, ...) {
 *       super.setupAnim((LivingEntity) entity, ...);     // -&gt; PlayerModel -&gt; HumanoidModel
 *       if (entity.isSitting() &amp;&amp; !this.young) this.translateSitting();
 *   }
 * </pre>
 *
 * <h2>Coexistence with their own poses, which is what was asked for</h2>
 * That same snippet is why sitting and the bound arms both survive. Cancelling
 * here cancels only {@code HumanoidModel#setupAnim}; {@code PlayerModel#setupAnim}
 * still finishes (it copies the posed parts onto the jacket and sleeve layers, so
 * the outer skin layer follows the tied arms), and their sitting offsets are then
 * ADDED on top of the pose this leaves behind rather than replacing it. A sitting
 * fake player in handcuffs reads as sitting, with its hands bound. Laying is the
 * same. That is [stated]'s preferred outcome - <i>"ideally, Id prefer if they both
 * coexist"</i> - and it falls out of the ordering rather than needing arbitration.
 *
 * <h2>Scope</h2>
 * Arms, plus - since 1.5.11 - the PILLORY pose. Cuffed's own mixin covers both in
 * one method for a real player, and in the same order:
 * <pre>
 *   if (detained == 0) { animatePilloryDetainedAnimation(...); cancel; }
 *   else               { ...arm animation flags... }
 * </pre>
 * Keeping both branches here, in that order, is deliberate. Splitting the pillory
 * into a second mixin on the same method would leave which of the two cancels first
 * up to Mixin's own ordering, whereas Cuffed's real-player behaviour is defined:
 * being in a pillory wins over any arm pose. Head and leg restraints still have no
 * model equivalent to mirror - head restraints are visual and leg restraints act on
 * movement.
 *
 * <p>The BED and WALL restraints are NOT here. Those are this addon's own features,
 * and their pose mixins ({@code HumanoidModelLiePoseMixin},
 * {@code HumanoidModelWallPoseMixin}) were widened from {@code Player} to
 * {@code LivingEntity} in 1.5.11 instead, so a fake player is posed by the very same
 * confirmed-correct code that poses a player - see
 * {@code fakeplayer/FakeStationaryUtil}. A fake player can never be in two of the
 * three at once ({@code FakeStationaryUtil#canApplyStationary}), and an arm
 * restraint rules all three out, so none of these branches can contend.
 */
@Mixin(HumanoidModel.class)
public abstract class FakePlayerArmPoseMixin<T extends LivingEntity>
        implements ISittingRestraintFlags {

    private static boolean reported;

    /**
     * What the entity being posed right now is wearing, for
     * {@code FakePlayerSittingPoseMixin} to read a moment later - see
     * {@link ISittingRestraintFlags} for why this rides on the MODEL rather than
     * being looked up from the entity.
     *
     * <p>Reset to 0 on EVERY call, including for entities that are not fake players,
     * because the model instance is shared across everything on screen and a stale
     * value would pose the wrong entity. An int write per humanoid model per frame is
     * nothing next to what is already happening here. 0 means "their own
     * translateSitting runs untouched", which is also the right answer for every
     * branch below that returns early.
     */
    @Unique
    private int cuffedaddon$sittingFlags;

    @Override
    public int cuffedaddon$sittingRestraintFlags() {
        return cuffedaddon$sittingFlags;
    }

    @Override
    public void cuffedaddon$setSittingRestraintFlags(int flags) {
        this.cuffedaddon$sittingFlags = flags;
    }

    @Unique
    private static boolean cuffedaddon$hasRestraint(IRestrainableEntity restrainable, RestraintType type) {
        ResourceLocation id = switch (type) {
            case Head -> restrainable.getHeadRestraintId();
            case Arm -> restrainable.getArmRestraintId();
            case Leg -> restrainable.getLegRestraintId();
        };
        // FakePlayerEntityMixin hands back an EMPTY ResourceLocation rather than null
        // for an empty slot, matching what Cuffed's own callers .equals() against.
        return id != null && !id.getPath().isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "setupAnim", at = @At("HEAD"), cancellable = true)
    private void cuffedaddon$poseRestrainedFakePlayer(LivingEntity entity, float limbSwing,
                                                       float limbSwingAmount, float ageInTicks,
                                                       float netHeadYaw, float headPitch, CallbackInfo ci) {
        // Cheapest test first: this runs for every humanoid model of every entity
        // on screen, several times per entity per frame once the armour and
        // restraint layers are counted.
        cuffedaddon$sittingFlags = 0;
        if (!FakePlayerSupport.isFakePlayer(entity)) {
            return;
        }
        // PILLORY FIRST - this is Cuffed's own precedence, see the class doc.
        // animatePilloryDetainedAnimation takes a LivingEntity (not a Player), so
        // the pose is Cuffed's own, identical by construction rather than by eye -
        // the same reason the arm branch below calls Cuffed's helpers instead of
        // setting joint angles here.
        IFakeDetained detained = FakeStationaryUtil.detained(entity);
        if (detained != null && detained.isDetained()) {
            HumanoidModel<LivingEntity> pilloryModel = (HumanoidModel<LivingEntity>) (Object) this;
            HumanoidAnimationHelper.animatePilloryDetainedAnimation(entity, pilloryModel, limbSwing,
                    limbSwingAmount, ageInTicks, netHeadYaw, headPitch, detained.getLockedYaw());
            ci.cancel();
            return;
        }

        if (!(entity instanceof IRestrainableEntity restrainable)) {
            // FakePlayerEntityMixin did not apply. It is already reported loudly at
            // entity load, so say nothing here and let the entity render unposed.
            return;
        }

        // Settled as soon as the restraint state is readable. LEGS comes straight off
        // the capability; ARMS waits until the arm branch below has actually chosen a
        // tied pose, because an arm restraint with no animation flag leaves the arms
        // exactly where vanilla put them and so must not suppress the seated swing.
        if (cuffedaddon$hasRestraint(restrainable, RestraintType.Leg)) {
            cuffedaddon$sittingFlags |= ISittingRestraintFlags.LEGS_BOUND;
        }

        ResourceLocation armRestraint = restrainable.getArmRestraintId();
        if (armRestraint == null) {
            return;
        }
        ArmRestraintAnimationFlags flags = RestraintAPI.getArmAnimationFlagByKey(armRestraint);
        if (flags == null || flags == ArmRestraintAnimationFlags.NONE) {
            return;
        }

        cuffedaddon$sittingFlags |= ISittingRestraintFlags.ARMS_TIED;

        HumanoidModel<LivingEntity> model = (HumanoidModel<LivingEntity>) (Object) this;
        switch (flags) {
            case ARMS_TIED_FRONT:
                HumanoidAnimationHelper.animateArmsTiedFront(entity, model, limbSwing, limbSwingAmount,
                        ageInTicks, netHeadYaw, headPitch);
                break;
            case ARMS_TIED_BEHIND:
                HumanoidAnimationHelper.animateArmsTiedBack(entity, model, limbSwing, limbSwingAmount,
                        ageInTicks, netHeadYaw, headPitch);
                break;
            default:
                return;
        }

        if (!reported) {
            reported = true;
            CuffedAddon.LOGGER.info("Fake Players compat: arm pose applied to a fake player ({}).", flags);
        }

        // Same as Cuffed: the helper has done the whole humanoid pose, so vanilla's
        // must not run afterwards and undo it.
        ci.cancel();
    }
}
