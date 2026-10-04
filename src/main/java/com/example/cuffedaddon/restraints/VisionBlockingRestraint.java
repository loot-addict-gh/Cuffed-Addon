package com.example.cuffedaddon.restraints;

import net.minecraft.resources.ResourceLocation;

/**
 * Implemented by every one of THIS ADDON'S OWN head restraints that block
 * vision with a full-screen texture (SleepMaskRestraint, and all 4 combos
 * via AbstractComboHeadRestraint) - lets client code outside the
 * restraints package (CompactHudRenderer) find and draw that texture
 * without needing package-private/protected access to each concrete
 * class.
 *
 * ROUND 24: this exists because of a real design gap found in-game -
 * CompactHudRenderer's compact branch never called a restraint's own
 * renderOverlay() at all (by design, it draws its own small icon
 * instead), which meant the full-screen blindfold - which lives INSIDE
 * renderOverlay(), interleaved with the icon/text drawing - was silently
 * never drawn in compact mode for ANY vision-blocking restraint,
 * including Cuffed's own Bundle. The blindfold is a gameplay mechanic
 * (you genuinely can't see), not a cosmetic HUD choice, so it has to
 * render regardless of which icon style is active. This interface is
 * the seam CompactHudRenderer uses to draw it itself in compact mode too
 * - see that class for the Bundle-specific (Cuffed's own, can't
 * implement an addon interface) handling.
 */
public interface VisionBlockingRestraint {
    ResourceLocation getVisionOverlayTexture();
}
