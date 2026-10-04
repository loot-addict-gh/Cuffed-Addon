package com.example.cuffedaddon.mixin;

import com.example.cuffedaddon.enchantment.ConjuredRestraints;
import com.lazrproductions.cuffed.api.CuffedAPI;
import com.lazrproductions.cuffed.cap.RestrainableCapability;
import com.lazrproductions.cuffed.restraints.base.AbstractArmRestraint;
import com.lazrproductions.cuffed.restraints.base.AbstractHeadRestraint;
import com.lazrproductions.cuffed.restraints.base.AbstractLegRestraint;
import com.lazrproductions.cuffed.restraints.base.AbstractRestraint;
import com.lazrproductions.cuffed.restraints.base.RestraintType;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

/**
 * Suppression point 2a for Restraint Gaze: a conjured restraint taken off by
 * someone holding a key does not land in their inventory.
 *
 * <p>Read {@link ConjuredRestraints} first - it explains the marker and why only
 * two places need suppressing. This one exists because the other,
 * {@code EntityJoinLevelEvent}, cannot see
 * {@code RestrainableCapability#UnequipRestraint}'s first branch: when there IS a
 * releaser it calls {@code releaser.addItem(stack)} and no ItemEntity is ever
 * created. That branch is the common case - it is what happens every time one
 * player unlocks another - so it cannot be left out.
 *
 * <h2>Why HEAD-and-cancel rather than redirecting the two drop calls</h2>
 * A pair of {@code @Redirect}s on {@code Player#addItem} and
 * {@code new ItemEntity(...)} would have been tidier and would not duplicate any
 * logic, but both injection points name VANILLA members, which are SRG-obfuscated
 * in production and would therefore need hand-written entries in
 * mixins.cuffedaddon.refmap.json - a file this project has to maintain by hand
 * because the build has no Mixin annotation processor, and a known source of
 * silent breakage. Injecting at the HEAD of a method on Cuffed's OWN class needs
 * no refmap entry at all, because mods are not obfuscated in production Forge.
 *
 * <p>The cost is that the seven lines below re-implement what Cuffed's own method
 * does after the drop: unequip callback, clear the field, send the sync packet.
 * They are copied from {@code UnequipRestraint} verbatim in order, and they only
 * ever run for a conjured restraint - every ordinary restraint returns early and
 * travels Cuffed's real code path untouched. <b>If Cuffed ever changes what
 * UnequipRestraint does, this needs the same change.</b>
 *
 * <p>The three restraint fields are {@code public} on
 * {@code RestrainableCapability}, so shadowing and nulling them is exactly what
 * the original does, not a workaround.
 */
@Mixin(value = RestrainableCapability.class, remap = false)
public abstract class RestrainableCapabilityConjuredMixin {

    @Shadow
    public AbstractArmRestraint armRestraint;

    @Shadow
    public AbstractLegRestraint legRestraint;

    @Shadow
    public AbstractHeadRestraint headRestraint;

    @Inject(method = "UnequipRestraint", at = @At("HEAD"), cancellable = true, require = 1)
    private void cuffedaddon$vanishConjuredInsteadOfReturningIt(ServerPlayer player,
                                                               @Nullable ServerPlayer releaser,
                                                               RestraintType type, CallbackInfo ci) {
        AbstractRestraint old = cuffedaddon$worn(type);
        if (old == null || !ConjuredRestraints.isConjured(old)) {
            return;
        }

        // Everything UnequipRestraint does EXCEPT building a stack and handing it
        // over, in the same order it does it.
        old.onUnequippedServer(player);

        if (type == RestraintType.Arm) {
            armRestraint = null;
        } else if (type == RestraintType.Leg) {
            legRestraint = null;
        } else {
            headRestraint = null;
        }

        CuffedAPI.Networking.sendRestraintEquipPacket(player, releaser, type, null, old);
        ci.cancel();
    }

    @Nullable
    private AbstractRestraint cuffedaddon$worn(RestraintType type) {
        if (type == RestraintType.Arm) {
            return armRestraint;
        }
        if (type == RestraintType.Leg) {
            return legRestraint;
        }
        if (type == RestraintType.Head) {
            return headRestraint;
        }
        return null;
    }
}
