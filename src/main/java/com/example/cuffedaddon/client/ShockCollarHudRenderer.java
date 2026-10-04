package com.example.cuffedaddon.client;

import com.example.cuffedaddon.CuffedAddon;
import com.example.cuffedaddon.init.ModEffects;
import com.lazrproductions.cuffed.CuffedMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * The Shock Collar's own HUD, registered as a Forge gui overlay rather than
 * going through {@code CompactHudRenderer}.
 *
 * <p>It has to be separate: {@code CompactHudRenderer} is driven by
 * {@code RestrainableCapabilityOverlayMixin}, which fires once per CUFFED
 * restraint slot. The collar lives in this addon's own fourth slot, so that
 * mixin never fires for it and there is nothing to hook into.
 *
 * <h2>Two unrelated things are drawn here</h2>
 * <ol>
 *   <li><b>The collar's slot icon + durability bar</b>, stacked with the
 *       existing head/arms/legs icons and drawn with the same constants so it
 *       lines up pixel-for-pixel. It's placed at the TOP of the stack (above
 *       head) rather than anatomically between head and arms, purely so the
 *       three existing positions [stated] has already signed off on don't
 *       shift. Easy to reorder if the anatomical order is preferred.</li>
 *   <li><b>The Electrization full-screen overlay</b>, drawn whenever the effect
 *       is active - the "GUI hud texture just like the head restraints"
 *       [stated] asked for. Same 32x18-stretched-to-fullscreen blit the
 *       vision-blocking head restraints use in {@code CompactHudRenderer}, so
 *       the placeholder can be repainted exactly like those.</li>
 * </ol>
 *
 * <p>The placeholder overlay texture is deliberately TRANSLUCENT rather than the
 * solid red used for Straitjacket's first pass: a fully opaque full-screen blit
 * would blind the player completely and make the feature untestable.
 */
public class ShockCollarHudRenderer implements IGuiOverlay {

    public static final String OVERLAY_ID = "shock_collar";

    private static final ResourceLocation CUFFED_WIDGETS =
            ResourceLocation.fromNamespaceAndPath(CuffedMod.MODID, "textures/gui/widgets.png");
    private static final ResourceLocation CUFFED_PROGRESS_BAR =
            ResourceLocation.fromNamespaceAndPath(CuffedMod.MODID, "textures/gui/progress_bar.png");
    private static final ResourceLocation COLLAR_ICON =
            ResourceLocation.fromNamespaceAndPath(CuffedAddon.MODID, "textures/gui/shock_collar_icon.png");
    private static final ResourceLocation ELECTRIZATION_OVERLAY =
            ResourceLocation.fromNamespaceAndPath(CuffedAddon.MODID, "textures/gui/electrization_overlay.png");

    private static final int ATLAS_SIZE = 192;
    private static final int ICON_SIZE = 16;

    /**
     * The Electrization overlay's own canvas size.
     *
     * <p><b>Deliberately 256x144, not the 32x18 the vision-blocking head
     * restraints use.</b> That 32x18 convention is Cuffed's, and it's fine for a
     * blindfold - a near-solid shape whose edges don't matter. It is hopeless
     * for lightning: stretched to a 1411px-wide screen, one texel is ~44 screen
     * pixels, so a one-texel line is the THINNEST mark the texture can express
     * and it still lands as a 44px slab. [stated] asked for thinner, sharper
     * lightning, which is not a painting problem - it's a resolution ceiling.
     * 256x144 keeps the exact same 16:9 ratio (so the fullscreen stretch is
     * unchanged) while making the finest possible line 8x thinner.
     */
    private static final int OVERLAY_TEX_WIDTH = 256;
    private static final int OVERLAY_TEX_HEIGHT = 144;

    /** Cuffed's shared "chain" bound-overlay, at (44,24) in its 192x192 atlas. */
    private static final int CHAIN_OVERLAY_U = 44;
    private static final int CHAIN_OVERLAY_V = 24;

    // Identical to CompactHudRenderer's own constants - kept in step on purpose
    // so the four icons read as one stack rather than two systems.
    private static final int MARGIN_LEFT = 6;
    private static final int MARGIN_BOTTOM = 6;
    private static final int ICON_GAP = 2;

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }

        if (minecraft.player.hasEffect(ModEffects.ELECTRIZATION.get())
                && isFlashVisible(minecraft.player)) {
            drawElectrizationOverlay(graphics, screenWidth, screenHeight);
        }

        if (ClientCollaredState.isCollared()) {
            drawCollarIcon(graphics, screenHeight);
        }
    }

    /** How many ticks the overlay stays up after each hit. */
    private static final int FLASH_ON_TICKS = 4;

    /**
     * True on the ticks the overlay should be drawn.
     *
     * <h2>Why this reads hurtTime instead of counting ticks</h2>
     * 1.4.38 derived the flash from {@code Math.floorMod(player.tickCount,
     * DAMAGE_INTERVAL_TICKS)}, reasoning that the server gates its damage on the
     * same counter. That was wrong, and [stated] saw it as visibly out of sync.
     * **A player's {@code tickCount} on the client and on the server are two
     * independent counters.** Both advance once per tick, but they start from
     * different moments - the client entity is built when the player joins or
     * respawns - so they sit at a fixed but arbitrary offset from each other.
     * Matching the PERIOD that way can never match the PHASE.
     *
     * <p>{@code hurtTime} has no such problem: the server sets it the moment
     * damage lands and it is synced to the client, so it IS the damage event
     * rather than a guess at when the damage event happens. It counts down from
     * {@code hurtDuration}, so testing near the top of that range gives a flash
     * that starts exactly on the hit.
     *
     * <p>Consequence worth knowing: the shock stops damaging at half a heart, so
     * at that point the flashing stops too even though Electrization is still
     * running. That is honest - it is showing you real hits - but say the word
     * if it should keep pulsing anyway.
     */
    private static boolean isFlashVisible(LocalPlayer player) {
        return player.hurtTime > 0
                && player.hurtTime > player.hurtDuration - FLASH_ON_TICKS;
    }

    private static void drawElectrizationOverlay(GuiGraphics graphics, int screenWidth, int screenHeight) {
        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        graphics.blit(ELECTRIZATION_OVERLAY, 0, 0, screenWidth, screenHeight, 0.0f, 0.0f,
                OVERLAY_TEX_WIDTH, OVERLAY_TEX_HEIGHT, OVERLAY_TEX_WIDTH, OVERLAY_TEX_HEIGHT);
    }

    private static void drawCollarIcon(GuiGraphics graphics, int screenHeight) {
        int legsY = screenHeight - MARGIN_BOTTOM - ICON_SIZE;
        int armsY = legsY - ICON_GAP - ICON_SIZE;
        int headY = armsY - ICON_GAP - ICON_SIZE;
        int collarY = headY - ICON_GAP - ICON_SIZE;

        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        graphics.blit(COLLAR_ICON, MARGIN_LEFT, collarY, 0.0f, 0.0f, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        // Same "bound" chain overlay every restraint in this ecosystem layers
        // on top of its own base icon.
        graphics.blit(CUFFED_WIDGETS, MARGIN_LEFT, collarY, CHAIN_OVERLAY_U, CHAIN_OVERLAY_V,
                ICON_SIZE, ICON_SIZE, ATLAS_SIZE, ATLAS_SIZE);

        drawDurabilityBar(graphics, MARGIN_LEFT, collarY);
    }

    /**
     * Same two-row bar RopeArmsRestraint draws, and for the same reason: the
     * real progress_bar.png asset's only opaque pixel is a FIXED green, so
     * tinting it toward red/yellow mostly cancels out instead of showing a
     * colour. Only the black shadow row comes from the texture; the coloured
     * row is a plain fill.
     */
    private static void drawDurabilityBar(GuiGraphics graphics, int x, int y) {
        int max = ClientCollaredState.getMaxDurability();
        float p = Mth.clamp((float) ClientCollaredState.getDurability() / (float) max, 0.0f, 1.0f);

        int barY = y + ICON_SIZE - 2;
        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        graphics.blit(CUFFED_PROGRESS_BAR, x, barY + 1, ICON_SIZE, 1, 0.0f, 1.0f, 1, 1, 2, 2);
        graphics.fill(x, barY, x + ICON_SIZE, barY + 1, 0xFF3B3B3B);
        int filledWidth = Math.round(ICON_SIZE * p);
        if (filledWidth > 0) {
            graphics.fill(x, barY, x + filledWidth, barY + 1, durabilityBarColor(p));
        }
    }

    /** Green at full, through yellow, to red as it nears breaking - same convention as Rope's bar. */
    private static int durabilityBarColor(float p) {
        p = Mth.clamp(p, 0.0f, 1.0f);
        int r;
        int g;
        if (p > 0.5f) {
            float t = (p - 0.5f) * 2.0f;
            r = Math.round(255 * (1.0f - t));
            g = 255;
        } else {
            float t = p * 2.0f;
            r = 255;
            g = Math.round(255 * t);
        }
        return 0xFF000000 | (r << 16) | (g << 8);
    }
}
