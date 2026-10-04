package com.example.cuffedaddon.restraints;

import com.lazrproductions.cuffed.CuffedMod;
import com.lazrproductions.cuffed.entity.animation.ArmRestraintAnimationFlags;
import com.lazrproductions.cuffed.entity.animation.LegRestraintAnimationFlags;
import com.lazrproductions.cuffed.init.ModModelLayers;
import com.lazrproductions.cuffed.restraints.base.AbstractHeadRestraint;
import com.lazrproductions.cuffed.restraints.client.RestraintModelInterface;
import com.lazrproductions.cuffed.restraints.client.model.BundleModel;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nonnull;

/**
 * Shared base for the 4 "combination" head restraints (a vision-blocking
 * item + a muffling item, combined in a crafting grid into one item that
 * applies both effects at once - see the 4 concrete subclasses). Everything
 * here is common to all 4: equip properties, sounds, lockpick/breakout
 * settings, animation flags, event stubs, and the render-overlay/model
 * plumbing. Each subclass only supplies its identity (ID/item/lang keys)
 * and its two texture choices (which vision component's full-screen
 * overlay to draw, and its own worn-model texture, hand-painted by
 * [stated] separately per combo - deliberately no default here).
 *
 * All 4 combos reuse Cuffed's own BundleModel class + already-registered
 * BUNDLE_LAYER for their base ("default height") worn-model layer, same
 * pattern SleepMaskRestraint uses - every vision component (Bundle, Sleep
 * Mask) already shares that exact geometry, so every combo naturally does
 * too. This is deliberately the ONLY render layer for 3 of the 4 combos;
 * SleepMaskRopeRestraint is the one exception that adds a second, additive
 * layer (Rope's existing head-wrap) - see its own class doc and
 * RopeWrapEntityLayer for why.
 */
public abstract class AbstractComboHeadRestraint extends AbstractHeadRestraint implements VisionBlockingRestraint {

    protected AbstractComboHeadRestraint() {
    }

    protected AbstractComboHeadRestraint(ItemStack stack, ServerPlayer player, ServerPlayer captor) {
        super(stack, player, captor);
    }

    /**
     * Cuffed's own bundle_overlay.png for Bundle-based combos, or this
     * addon's own sleep_mask_overlay.png for Sleep-Mask-based combos -
     * whichever vision component this combo includes. Both already exist;
     * no new overlay art is needed for any combo.
     */
    public abstract ResourceLocation getVisionOverlayTexture();

    /**
     * The combo's own worn-model texture - a new, not-yet-created
     * ResourceLocation per combo. [stated] is hand-painting these
     * (combining both components' look into one texture on the shared
     * BundleModel UV layout), so none of these files exist yet.
     */
    protected abstract ResourceLocation getModelTexture();

    // #region Restraint Properties (identical across all 4 combos)

    public SoundEvent getEquipSound() {
        return SoundEvents.WOOL_PLACE;
    }
    public SoundEvent getUnequipSound() {
        return SoundEvents.WOOL_PLACE;
    }

    public boolean AllowBreakingBlocks() {
        return true;
    }
    public boolean AllowItemUse() {
        return true;
    }
    public boolean AllowMovement() {
        return true;
    }
    public boolean AllowJumping() {
        return true;
    }
    public boolean AllowSprinting() {
        return true;
    }

    public boolean canBeBrokenOutOf() {
        return false;
    }
    public boolean getLockpickable() {
        return false;
    }
    public int getLockpickingProgressPerPick() {
        return 5;
    }
    public int getLockpickingSpeedIncreasePerPick() {
        return 0;
    }

    public ArmRestraintAnimationFlags getArmAnimationFlags() {
        return ArmRestraintAnimationFlags.NONE;
    }
    public LegRestraintAnimationFlags getLegAnimationFlags() {
        return LegRestraintAnimationFlags.NONE;
    }
    // #endregion

    // #region Events (no-ops, same as SleepMaskRestraint)

    public void onLoginServer(ServerPlayer player) {
    }
    public void onLoginClient(Player player) {
    }
    public void onLogoutServer(ServerPlayer player) {
    }
    public void onLogoutClient(Player player) {
    }
    public void onDeathServer(ServerPlayer player) {
    }
    public void onDeathClient(Player player) {
    }
    public void onJumpServer(ServerPlayer player) {
    }
    public void onJumpClient(Player player) {
    }
    public float onLandServer(ServerPlayer player, float distance, float damageMultiplier) {
        return 1;
    }
    public void onLandClient(Player player, float distance, float damageMultiplier) {
    }
    // #endregion

    // #region Client-Side operations

    static final ResourceLocation CUFFED_WIDGETS = ResourceLocation.fromNamespaceAndPath(CuffedMod.MODID, "textures/gui/widgets.png");

    public void renderOverlay(Player player, GuiGraphics graphics, float partialTick, Window window) {
        int h = window.getGuiScaledHeight();
        int w = window.getGuiScaledWidth();
        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        graphics.blit(getVisionOverlayTexture(), 0, 0, w, h, 0.0f, 0.0f, 32, 18, 32, 18);

        super.renderOverlay(player, graphics, partialTick, window);

        float f = 1;
        graphics.setColor(f, f, f, 1);

        int iconWidth = (int) (16 * 1.75f);
        int iconHeight = (int) (16 * 1.75f);
        int x = (window.getGuiScaledWidth() / 2) - (iconWidth / 2);
        int y = (window.getGuiScaledHeight() / 2) - (iconHeight) - 100;

        graphics.blit(CUFFED_WIDGETS, x, y, iconWidth, iconHeight, 44.0f, 24.0f, 16, 16, 192, 192);
        graphics.setColor(1, 1, 1, 1);
    }

    @Nonnull
    @OnlyIn(Dist.CLIENT)
    @Override
    public RestraintModelInterface getModelInterface() {
        return new ComboModelInterface(getModelTexture());
    }

    // #endregion

    @OnlyIn(Dist.CLIENT)
    private static class ComboModelInterface extends RestraintModelInterface {
        @SuppressWarnings("unchecked")
        static final Class<? extends HumanoidModel<? extends LivingEntity>> MODEL_CLASS = (Class<? extends HumanoidModel<? extends LivingEntity>>) (Class<?>) BundleModel.class;
        static final ModelLayerLocation MODEL_LAYER = ModModelLayers.BUNDLE_LAYER;

        private final ResourceLocation texture;

        ComboModelInterface(ResourceLocation texture) {
            this.texture = texture;
        }

        @Override
        public Class<? extends HumanoidModel<? extends LivingEntity>> getRenderedModel() {
            return MODEL_CLASS;
        }
        @Override
        public ModelLayerLocation getRenderedModelLayer() {
            return MODEL_LAYER;
        }
        @Override
        public ResourceLocation getRenderedModelTexture() {
            return texture;
        }
    }
}
