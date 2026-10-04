package com.example.cuffedaddon.mixin;

import com.example.cuffedaddon.enchantment.IllusionUtil;
import com.lazrproductions.cuffed.cap.RestrainableCapability;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets you put restraints on, and take them off, yourself while wearing an
 * Illusion arm restraint.
 *
 * <p>[stated]: <i>"I also cant apply or remove any other restraints, at least on
 * myself"</i>. Cuffed's self-interaction - the arm-swing gesture that applies or
 * removes a restraint on yourself, aiming up for the head, down for the legs -
 * lives in {@code attemptToRemoveRestraint}, a method Cuffed's own
 * {@code PlayerMixin} MERGES INTO {@code Player}. Its first line is
 * {@code if (!cap.armsRestrained())}, the real and correct rule that you cannot
 * use your hands on yourself while your arms are bound. An Illusion arm restraint
 * satisfied that check and blocked the whole gesture.
 *
 * <h2>Why a surgical redirect and not a change to armsRestrained()</h2>
 * Making {@code armsRestrained()} itself Illusion-aware would have been one line
 * and would have been wrong. Seven other places read it, and most of them want
 * the honest answer: the Possessions Box only frisks a player whose arms are
 * restrained (so an Illusion wearer must still be friskable, or the disguise
 * breaks from the outside), the restraint items use it to refuse a second
 * restraint in an occupied slot, and the pillory reads it too. Only this call
 * site and the scroll wheel want "are the arms REALLY bound", so only those two
 * are changed.
 *
 * <p>Applying a second ARM restraint over an Illusion one is still refused, by
 * {@code TryEquipRestraint}'s own null check further down - so this opens up the
 * head and leg slots and self-removal without letting anything stack.
 *
 * <h2>Priority, and why a mistake here is loud rather than silent</h2>
 * {@code attemptToRemoveRestraint} only exists on {@code Player} once Cuffed's
 * mixin has been applied, so this one must be applied AFTER it - hence the
 * priority above the default 1000 that Cuffed uses. If that were ever wrong the
 * {@code require = 1} would fail at startup with a clear message rather than
 * silently doing nothing.
 *
 * <p>Both the injected method and the redirected call are Cuffed-declared, so
 * {@code remap = false} and no refmap entries - even though the target class is
 * vanilla.
 */
@Mixin(value = Player.class, priority = 1500)
public class PlayerSelfRestraintIllusionMixin {

    @Redirect(
            method = "attemptToRemoveRestraint",
            at = @At(value = "INVOKE",
                    target = "Lcom/lazrproductions/cuffed/cap/RestrainableCapability;armsRestrained()Z"),
            remap = false,
            require = 1
    )
    private boolean cuffedaddon$illusionArmsDoNotBlockSelfInteraction(RestrainableCapability cap) {
        return cap.armsRestrained() && !IllusionUtil.isIllusion(cap.getArmRestraint());
    }
}
