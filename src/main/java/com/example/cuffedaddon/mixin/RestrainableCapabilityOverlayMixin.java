package com.example.cuffedaddon.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.example.cuffedaddon.client.CompactHudRenderer;
import com.lazrproductions.cuffed.cap.RestrainableCapability;
import com.lazrproductions.cuffed.restraints.base.AbstractArmRestraint;
import com.lazrproductions.cuffed.restraints.base.AbstractHeadRestraint;
import com.lazrproductions.cuffed.restraints.base.AbstractLegRestraint;
import com.mojang.blaze3d.platform.Window;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;

/**
 * RestrainableCapability#renderOverlay is just 3 lines - one call each to
 * whichever concrete head/arm/leg restraint object is currently equipped
 * (headRestraint/armRestraint/legRestraint, typed as AbstractHeadRestraint/
 * AbstractArmRestraint/AbstractLegRestraint respectively, NOT a shared
 * common type - which is why this needs 3 separate @Redirect targets, one
 * per declared field type, rather than one covering all 3). Redirecting
 * here - a single small dispatcher - covers every restraint variant at
 * once (duct tape, handcuffs, rope, sleep mask, the 4 combos, everything),
 * rather than needing a mixin per concrete restraint class.
 *
 * Unlike this addon's other mixins (all targeting VANILLA classes, which
 * need SRG-vs-official dual name lookups since vanilla is obfuscated in
 * production), Cuffed's own classes are never obfuscated - "renderOverlay"
 * and the 3 target class names below are the same literal names in both a
 * Gradle dev run and a real production install, no reflection/refmap
 * dance needed here.
 *
 * "client"-only in mixins.cuffedaddon.json, matching the fact that
 * RenderGuiOverlayEvent (and therefore this whole call path) only ever
 * fires on the logical client in the first place.
 */
@Mixin(RestrainableCapability.class)
public class RestrainableCapabilityOverlayMixin {

    @Redirect(method = "renderOverlay", at = @At(value = "INVOKE", target = "Lcom/lazrproductions/cuffed/restraints/base/AbstractHeadRestraint;"
            + "renderOverlay(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/client/gui/GuiGraphics;FLcom/mojang/blaze3d/platform/Window;)V"))
    private void cuffedaddon$redirectHeadOverlay(AbstractHeadRestraint restraint, Player player, GuiGraphics graphics,
            float partialTick, Window window) {
        CompactHudRenderer.renderOrDelegate(restraint, player, graphics, partialTick, window);
    }

    @Redirect(method = "renderOverlay", at = @At(value = "INVOKE", target = "Lcom/lazrproductions/cuffed/restraints/base/AbstractArmRestraint;"
            + "renderOverlay(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/client/gui/GuiGraphics;FLcom/mojang/blaze3d/platform/Window;)V"))
    private void cuffedaddon$redirectArmOverlay(AbstractArmRestraint restraint, Player player, GuiGraphics graphics,
            float partialTick, Window window) {
        CompactHudRenderer.renderOrDelegate(restraint, player, graphics, partialTick, window);
    }

    @Redirect(method = "renderOverlay", at = @At(value = "INVOKE", target = "Lcom/lazrproductions/cuffed/restraints/base/AbstractLegRestraint;"
            + "renderOverlay(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/client/gui/GuiGraphics;FLcom/mojang/blaze3d/platform/Window;)V"))
    private void cuffedaddon$redirectLegOverlay(AbstractLegRestraint restraint, Player player, GuiGraphics graphics,
            float partialTick, Window window) {
        CompactHudRenderer.renderOrDelegate(restraint, player, graphics, partialTick, window);
    }
}
