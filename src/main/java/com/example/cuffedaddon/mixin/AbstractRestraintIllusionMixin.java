package com.example.cuffedaddon.mixin;

import com.example.cuffedaddon.enchantment.IllusionUtil;
import com.lazrproductions.cuffed.restraints.base.AbstractRestraint;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * You cannot struggle out of a restraint that was never holding you.
 *
 * <p>[stated], testing 1.5.18: <i>"using mouse clicks simultaneously tries
 * struggling out of the restraints, damaging them, which I dont want happening"</i>
 * - and the same on legs while walking. Struggling is not routed through any of
 * the restriction paths Illusion already intercepts: {@code AbstractRestraint}
 * handles key and mouse input directly and calls
 * {@code IBreakableRestraint#attemptToBreak} itself, so freeing the inputs
 * (which Illusion does, deliberately) handed those same clicks straight to the
 * struggle code. An Illusion restraint was quietly taking real durability damage
 * every time its wearer used their now-unrestricted hands.
 *
 * <p>Both input entry points are cancelled outright rather than made conditional,
 * because struggling is the only thing either of them does.
 *
 * <p>The third injection closes the same hole on the server. Durability is
 * incremented by a client-sent utility packet (code 102), so a client that did
 * not know about Illusion - or chose not to - could still damage one. The server
 * now refuses it, which is the same reasoning as the Shock Collar's server-side
 * struggle limit.
 *
 * <p>Cuffed's own class, so no refmap entries.
 */
@Mixin(value = AbstractRestraint.class, remap = false)
public abstract class AbstractRestraintIllusionMixin {

    @Inject(method = "onKeyInput", at = @At("HEAD"), cancellable = true, require = 1)
    private void cuffedaddon$noStruggleOnKey(Player player, int keyCode, int action, CallbackInfo ci) {
        if (IllusionUtil.isIllusion((AbstractRestraint) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "onMouseInput", at = @At("HEAD"), cancellable = true, require = 1)
    private void cuffedaddon$noStruggleOnMouse(Player player, int keyCode, int action, CallbackInfo ci) {
        if (IllusionUtil.isIllusion((AbstractRestraint) (Object) this)) {
            ci.cancel();
        }
    }

    /**
     * Utility code 102 is "increment my durability", sent by the struggling
     * client. An Illusion restraint takes no damage from it whatever arrives.
     */
    @Inject(method = "receiveUtilityPacketServer", at = @At("HEAD"), cancellable = true, require = 1)
    private void cuffedaddon$noStruggleFromPacket(ServerPlayer player, int utilityCode, int integerArg,
                                                  boolean booleanArg, double doubleArg, String stringArg,
                                                  CallbackInfo ci) {
        if (utilityCode == 102 && IllusionUtil.isIllusion((AbstractRestraint) (Object) this)) {
            ci.cancel();
        }
    }
}
