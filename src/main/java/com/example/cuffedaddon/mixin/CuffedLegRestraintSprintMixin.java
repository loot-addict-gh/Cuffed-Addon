package com.example.cuffedaddon.mixin;

import com.example.cuffedaddon.enchantment.IllusionUtil;
import com.lazrproductions.cuffed.restraints.custom.DuckTapeLegsRestraint;
import com.lazrproductions.cuffed.restraints.custom.HandcuffsLegsRestraint;
import com.lazrproductions.cuffed.restraints.custom.ShacklesLegsRestraint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets an Illusion leg restraint sprint. Cuffed's three leg restraints.
 *
 * <p>[stated], 1.5.20: <i>"also leg restraints disable sprinting"</i>.
 *
 * <h2>Why this was the one thing Illusion missed</h2>
 * Every other restriction a restraint applies goes through one of two funnels this
 * addon already intercepts: {@code encodeRestraintDisabilities()} (which becomes
 * the synced restraint code, and from there drives the movement, jumping, mining
 * and item-use blocks, the speed and swim-speed attribute modifiers, the FOV
 * clamp, the inventory screen, the jump slam and the command block) and
 * {@code gatherBlockedInputs()} (the blocked key codes, WASD and the sprint key
 * included).
 *
 * <p>{@code AllowSprinting()} is the exception: it is the only one of
 * {@code AbstractRestraint}'s five {@code Allow*} methods with no bit in the
 * restraint code, because it is read straight off the restraint in
 * {@code LocalPlayerMixin#canStartSprinting}. Nothing this addon overrode could
 * see it.
 *
 * <p><b>Blocking the sprint key was not enough on its own.</b> With vanilla's
 * Toggle Sprint option, the toggle stays on across being restrained, so
 * {@code isDown()} keeps reporting true even though the key press itself is
 * blocked - and Shackles on the legs permit ordinary movement, so the forward
 * impulse is there too. {@code AllowSprinting()} is the guard that actually stops
 * that case, which is why this lies conditionally rather than just returning true.
 *
 * <h2>Why an ambient flag rather than the restraint</h2>
 * Cuffed does not ask the worn restraint. It reads the worn restraint's <i>id</i>,
 * builds a brand new instance from the registry, and asks that - so the instance
 * this injects into has no enchantments and no wearer, and cannot answer for
 * itself. {@code IllusionUtil#localPlayerHasIllusionLegs()} documents why the
 * ambient answer is nevertheless exact: {@code canStartSprinting} is the only
 * caller of {@code AllowSprinting()} in either mod, and it only ever asks about
 * the local player.
 *
 * <p>Client-side only, for the same reason - registered under "client" in
 * mixins.cuffedaddon.json, so on a dedicated server these three classes are left
 * exactly as Cuffed wrote them.
 *
 * <p>This addon's own two leg restraints answer the same way without a mixin; see
 * {@code RopeLegsRestraint#AllowSprinting()}.
 */
@Mixin(value = {DuckTapeLegsRestraint.class, HandcuffsLegsRestraint.class, ShacklesLegsRestraint.class},
        remap = false)
public class CuffedLegRestraintSprintMixin {

    @Inject(method = "AllowSprinting", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void cuffedaddon$illusionLegsMaySprint(CallbackInfoReturnable<Boolean> cir) {
        if (IllusionUtil.localPlayerHasIllusionLegs()) {
            cir.setReturnValue(true);
        }
    }
}
