package com.example.cuffedaddon.mixin;

import com.example.cuffedaddon.enchantment.IllusionUtil;
import com.example.cuffedaddon.enchantment.IllusionUtil.Restriction;
import com.lazrproductions.cuffed.cap.RestrainableCapability;
import com.lazrproductions.cuffed.effect.RestrainedEffectInstance;
import com.lazrproductions.cuffed.restraints.base.AbstractArmRestraint;
import com.lazrproductions.cuffed.restraints.base.AbstractHeadRestraint;
import com.lazrproductions.cuffed.restraints.base.AbstractLegRestraint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;

/**
 * Makes an Illusion restraint stop restricting its wearer, at the five places
 * Cuffed decides what a restraint takes away.
 *
 * <p>Read {@code IllusionUtil} for the map of those places and why there are so
 * few of them. Each method below reproduces Cuffed's own aggregation exactly,
 * with one substitution: {@code IllusionUtil.blocks(...)} instead of a direct
 * {@code Allow*()} call, so an Illusion restraint contributes nothing.
 *
 * <h2>Why reproduce rather than redirect</h2>
 * A {@code @Redirect} on each {@code Allow*()} call would avoid duplicating the
 * logic, but Cuffed makes twelve of those calls in
 * {@code encodeRestraintDisabilities} alone and three in each
 * {@code restraintsDisabled*} - twenty-four injection points to keep matched
 * against a class that could gain a fourth slot. Five HEAD injections that each
 * read as the method they replace is the smaller thing to maintain, and every one
 * is a plain OR over the same three public fields Cuffed itself reads. <b>If
 * Cuffed changes what any of these methods do, these need the same change.</b>
 *
 * <h2>encodeRestraintDisabilities is the important one</h2>
 * It is not one restriction among five - it is packed into the amplifier of the
 * {@code RESTRAINED_EFFECT}, which IS synced to the client, and the client reads
 * every one of its four bits back out for the movement, mining and item-use
 * blocking a player actually feels. Intercepting it frees all four on both sides
 * at once. The {@code restraintsDisabled*} methods are intercepted as well because
 * three server-side checks read them directly rather than going through the
 * encoded value.
 *
 * <p>No refmap entries: {@code RestrainableCapability} is another mod's class and
 * mods are not obfuscated in production Forge.
 */
@Mixin(value = RestrainableCapability.class, remap = false)
public abstract class RestrainableCapabilityIllusionMixin {

    @Shadow
    public AbstractArmRestraint armRestraint;

    @Shadow
    public AbstractLegRestraint legRestraint;

    @Shadow
    public AbstractHeadRestraint headRestraint;

    private boolean cuffedaddon$anyBlocks(Restriction which) {
        return IllusionUtil.blocks(headRestraint, which)
                || IllusionUtil.blocks(armRestraint, which)
                || IllusionUtil.blocks(legRestraint, which);
    }

    @Inject(method = "encodeRestraintDisabilities", at = @At("HEAD"), cancellable = true, require = 1)
    private void cuffedaddon$encodeIgnoringIllusions(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(RestrainedEffectInstance.encodeRestraintProperties(
                cuffedaddon$anyBlocks(Restriction.MINING),
                cuffedaddon$anyBlocks(Restriction.ITEM_USE),
                cuffedaddon$anyBlocks(Restriction.MOVEMENT),
                cuffedaddon$anyBlocks(Restriction.JUMPING)));
    }

    @Inject(method = "restraintsDisabledMovement", at = @At("HEAD"), cancellable = true, require = 1)
    private void cuffedaddon$movement(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(cuffedaddon$anyBlocks(Restriction.MOVEMENT));
    }

    @Inject(method = "restraintsDisabledJumping", at = @At("HEAD"), cancellable = true, require = 1)
    private void cuffedaddon$jumping(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(cuffedaddon$anyBlocks(Restriction.JUMPING));
    }

    @Inject(method = "restraintsDisabledItemUse", at = @At("HEAD"), cancellable = true, require = 1)
    private void cuffedaddon$itemUse(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(cuffedaddon$anyBlocks(Restriction.ITEM_USE));
    }

    @Inject(method = "restraintsDisabledBreakingBlocks", at = @At("HEAD"), cancellable = true, require = 1)
    private void cuffedaddon$breaking(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(cuffedaddon$anyBlocks(Restriction.MINING));
    }

    /**
     * The inventory and hotbar blocking [stated] called out by name. Cuffed's
     * version concatenates all three restraints' blocked key codes; an Illusion
     * restraint contributes none of its own.
     */
    @Inject(method = "gatherBlockedInputs", at = @At("HEAD"), cancellable = true, require = 1)
    private void cuffedaddon$gatherIgnoringIllusions(CallbackInfoReturnable<ArrayList<Integer>> cir) {
        ArrayList<Integer> blocked = new ArrayList<>();
        if (headRestraint != null && !IllusionUtil.isIllusion(headRestraint)) {
            blocked.addAll(headRestraint.getBlockedKeyCodes());
        }
        if (armRestraint != null && !IllusionUtil.isIllusion(armRestraint)) {
            blocked.addAll(armRestraint.getBlockedKeyCodes());
        }
        if (legRestraint != null && !IllusionUtil.isIllusion(legRestraint)) {
            blocked.addAll(legRestraint.getBlockedKeyCodes());
        }
        cir.setReturnValue(blocked);
    }
}
