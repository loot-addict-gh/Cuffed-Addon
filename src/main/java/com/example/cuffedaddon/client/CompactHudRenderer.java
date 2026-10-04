package com.example.cuffedaddon.client;

import com.example.cuffedaddon.enchantment.IllusionUtil;
import com.example.cuffedaddon.restraints.VisionBlockingRestraint;
import com.lazrproductions.cuffed.CuffedMod;
import com.lazrproductions.cuffed.restraints.base.AbstractRestraint;
import com.lazrproductions.cuffed.restraints.custom.BundleRestraint;
import com.lazrproductions.cuffed.restraints.custom.FuzzyHandcuffsRestraint;
import com.mojang.blaze3d.platform.Window;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * The only thing RestrainableCapabilityOverlayMixin calls into. Every icon
 * here is drawn from Cuffed's own "cuffed:textures/gui/widgets.png" atlas -
 * the base per-type (head/arm/leg) icon region
 * AbstractHeadRestraint/AbstractArmRestraint/AbstractLegRestraint use for
 * the FIRST layer of their own rendering, PLUS the restraint-specific
 * "bound" overlay every concrete restraint class layers on top of it.
 *
 * That overlay turned out to be far more uniform than expected: checked
 * every concrete restraint in both Cuffed's own source (DuckTape x3,
 * Handcuffs x2, Bundle, Shackles x2, Pillory) and this addon's own (Rope
 * x3, Sleep Mask, all 4 combos, inherited via AbstractComboHeadRestraint)
 * - EVERY one of them draws the exact same "chain" icon at (44,24) in the
 * shared 192x192 atlas, with exactly ONE exception: FuzzyHandcuffsRestraint
 * uses its own icon at (92,24) instead. So rather than needing a full
 * per-restraint-class icon table, this only needs one `instanceof` check.
 *
 * Simplification made here on purpose: Cuffed's own overlay briefly
 * brightens/flashes this icon based on each restraint's own private
 * breakCooldown field after a struggle attempt - that field isn't
 * accessible generically across every restraint type without per-class
 * reflection, and wasn't asked for, so the compact overlay is always drawn
 * at full, flat brightness. Ask if the flash effect turns out to be missed.
 *
 * Still no text label, no durability/struggle-progress bar - just the type
 * icon + its overlay, stacked bottom-left, closer together than Cuffed's
 * original ~35-40px gaps. Each of the 3 slots sits at a FIXED vertical
 * position (head above arms above legs) regardless of which other
 * restraint types are currently active - a player wearing only arm
 * restraints just won't have a head/leg icon drawn, leaving that slot's
 * space empty rather than the stack compacting around it. If a gapless
 * compacted stack turns out to be what's actually wanted instead, that's a
 * small follow-up change here, not a redesign.
 *
 * ROUND 24: real design gap found in-game - this class never called a
 * restraint's own renderOverlay() in the compact branch at all (draws its
 * own small icon instead, by design), but the full-screen VISION-BLOCKING
 * blindfold lives INSIDE that same renderOverlay() method, interleaved
 * with the icon/text drawing. Compact mode was therefore silently never
 * showing the blindfold for ANY vision-blocking head restraint - not a
 * cosmetic miss, since the blindfold is the actual gameplay effect, not
 * just a HUD choice. Fixed by drawing that same full-screen texture
 * ourselves in the compact branch too, BEFORE the small icon (same
 * draw-order Cuffed's own restraints use: blindfold first, icon+text on
 * top) - see drawVisionBlockOverlayIfPresent. This addon's own restraints
 * expose their texture via VisionBlockingRestraint; Cuffed's own Bundle
 * doesn't implement that (can't touch its compiled class), so it's
 * special-cased with its own hardcoded (confirmed via CFR decompile)
 * cuffed:textures/gui/bundle_overlay.png, same 32x18 texture Cuffed's own
 * BundleRestraint.renderOverlay draws.
 */
public class CompactHudRenderer {

    private static final ResourceLocation WIDGETS = ResourceLocation.fromNamespaceAndPath(CuffedMod.MODID,
            "textures/gui/widgets.png");
    private static final ResourceLocation BUNDLE_OVERLAY = ResourceLocation.fromNamespaceAndPath(CuffedMod.MODID,
            "textures/gui/bundle_overlay.png");
    private static final int ATLAS_SIZE = 192;
    private static final int ICON_SIZE = 16;

    private static final int HEAD_U = 60;
    private static final int HEAD_V = 40;
    private static final int ARMS_U = 76;
    private static final int ARMS_V = 24;
    private static final int LEGS_U = 60;
    private static final int LEGS_V = 24;

    private static final int CHAIN_OVERLAY_U = 44;
    private static final int CHAIN_OVERLAY_V = 24;
    private static final int FUZZY_HANDCUFFS_OVERLAY_U = 92;
    private static final int FUZZY_HANDCUFFS_OVERLAY_V = 24;

    private static final int MARGIN_LEFT = 6;
    private static final int MARGIN_BOTTOM = 6;
    private static final int ICON_GAP = 2;

    /**
     * Called from the mixin in place of each of the 3
     * RestrainableCapability#renderOverlay call sites (head/arm/leg). Either
     * draws the compact icon (+ overlay) for this restraint's type, or - if
     * the player has opted into the original GUI - simply calls through to
     * the restraint's own real renderOverlay exactly as Cuffed's own
     * unmodified code would.
     */
    public static void renderOrDelegate(AbstractRestraint restraint, Player player, GuiGraphics graphics,
            float partialTick, Window window) {
        // An Illusion restraint never blocks vision. That has to be handled here
        // because the blindfold is not a separate call: it is drawn INSIDE the
        // restraint's own renderOverlay, interleaved with its icon drawing, so
        // there is no way to delegate and suppress only the blindfold half.
        //
        // In compact mode the two ARE separable - this class draws the blindfold
        // and the icon itself - so an Illusion restraint keeps its HUD icon and
        // simply loses the full-screen overlay.
        //
        // In original-GUI mode the delegate would draw both, so an Illusion
        // restraint falls through to the compact path instead of being skipped
        // entirely. The deliberate trade: with /originalGui true, an Illusion
        // restraint shows the COMPACT icon rather than Cuffed's large one. Losing
        // one icon's styling is better than either blindfolding someone the
        // enchantment says can see, or dropping their HUD entry with no
        // explanation. Only you see your own HUD, so this cannot give the
        // disguise away.
        boolean illusion = IllusionUtil.isIllusion(restraint);

        if (CuffedAddonClientConfig.SHOW_ORIGINAL_GUI.get() && !illusion) {
            restraint.renderOverlay(player, graphics, partialTick, window);
            return;
        }

        int screenHeight = window.getGuiScaledHeight();
        int legsY = screenHeight - MARGIN_BOTTOM - ICON_SIZE;
        int armsY = legsY - ICON_GAP - ICON_SIZE;
        int headY = armsY - ICON_GAP - ICON_SIZE;

        // Blindfold first (if this is a vision-blocking head restraint),
        // THEN the icon on top - see this class's own round 24 doc.
        if (!illusion) {
            drawVisionBlockOverlayIfPresent(restraint, graphics, window);
        }

        int baseU;
        int baseV;
        int y;
        switch (restraint.getType()) {
            case Head -> { baseU = HEAD_U; baseV = HEAD_V; y = headY; }
            case Arm -> { baseU = ARMS_U; baseV = ARMS_V; y = armsY; }
            default -> { baseU = LEGS_U; baseV = LEGS_V; y = legsY; }
        }

        drawIcon(graphics, baseU, baseV, MARGIN_LEFT, y);

        boolean fuzzyHandcuffs = restraint instanceof FuzzyHandcuffsRestraint;
        int overlayU = fuzzyHandcuffs ? FUZZY_HANDCUFFS_OVERLAY_U : CHAIN_OVERLAY_U;
        int overlayV = fuzzyHandcuffs ? FUZZY_HANDCUFFS_OVERLAY_V : CHAIN_OVERLAY_V;
        drawIcon(graphics, overlayU, overlayV, MARGIN_LEFT, y);
    }

    /**
     * The actual gameplay vision-blocking effect - always drawn full-screen
     * regardless of compact/original GUI choice. This addon's own restraints
     * (Sleep Mask, all 4 combos) implement VisionBlockingRestraint and supply
     * their own texture; Cuffed's own Bundle is special-cased since it can't
     * implement an addon interface - see this class's own round 24 doc.
     */
    private static void drawVisionBlockOverlayIfPresent(AbstractRestraint restraint, GuiGraphics graphics,
            Window window) {
        ResourceLocation overlayTexture;
        if (restraint instanceof VisionBlockingRestraint visionBlocking) {
            overlayTexture = visionBlocking.getVisionOverlayTexture();
        } else if (restraint instanceof BundleRestraint) {
            overlayTexture = BUNDLE_OVERLAY;
        } else {
            return;
        }

        int h = window.getGuiScaledHeight();
        int w = window.getGuiScaledWidth();
        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        graphics.blit(overlayTexture, 0, 0, w, h, 0.0f, 0.0f, 32, 18, 32, 18);
    }

    private static void drawIcon(GuiGraphics graphics, int u, int v, int x, int y) {
        graphics.setColor(1, 1, 1, 1);
        graphics.blit(WIDGETS, x, y, u, v, ICON_SIZE, ICON_SIZE, ATLAS_SIZE, ATLAS_SIZE);
    }
}

