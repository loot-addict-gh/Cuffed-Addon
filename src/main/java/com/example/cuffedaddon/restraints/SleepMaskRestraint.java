package com.example.cuffedaddon.restraints;

import com.example.cuffedaddon.init.ModItems;
import com.example.cuffedaddon.init.ModRestraints;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nonnull;

/**
 * Mirrors com.lazrproductions.cuffed.restraints.custom.BundleRestraint:
 * full-screen renderOverlay blit (vision block), no chat-muffle logic of
 * its own - ClientEvents deliberately doesn't list this restraint's ID, so a
 * player wearing only a Sleep Mask can still type normally, same as Bundle
 * does. (Before 1.4.41 Bundle had an opt-in bundleMuffle gamerule and this
 * one never did; that gamerule is gone now, so both simply don't muffle.)
 * If simultaneous muffle+blindfold is wanted later, that's
 * the real ask behind the "2 head restraint slots" question raised
 * alongside this item - see the addon's own notes on that, not solved here.
 *
 * The worn model deliberately reuses Cuffed's own BundleModel class AND its
 * already-registered ModModelLayers.BUNDLE_LAYER directly (same pattern
 * RopeHeadRestraint uses for DuckTapeHeadModel/DUCK_TAPE_HEAD_LAYER) - only
 * the texture differs, so no new model/layer registration was needed here.
 */
public class SleepMaskRestraint extends AbstractHeadRestraint implements VisionBlockingRestraint {

    public SleepMaskRestraint() {
    }

    public SleepMaskRestraint(ItemStack stack, ServerPlayer player, ServerPlayer captor) {
        super(stack, player, captor);
    }

    // #region Restraint Properties

    public static final ResourceLocation ID = ModRestraints.SLEEP_MASK.getId();
    public ResourceLocation getId() {
        return ID;
    }

    public String getActionBarLabel() {
        return "info.cuffedaddon.restraints.sleep_mask.action_bar";
    }
    public String getName() {
        return "info.cuffedaddon.restraints.sleep_mask.name";
    }

    public static final Item ITEM = ModItems.SLEEP_MASK.get();
    public Item getItem() {
        return ITEM;
    }
    public static final Item KEY = null;
    public Item getKeyItem() {
        return KEY;
    }

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

    // #region Events

    public void onTickServer(ServerPlayer player) {
        super.onTickServer(player);
    }

    public void onTickClient(Player player) {
        super.onTickClient(player);
    }

    public void onEquippedServer(ServerPlayer player, ServerPlayer captor) {
        super.onEquippedServer(player, captor);
    }

    public void onEquippedClient(Player player, Player captor) {
        super.onEquippedClient(player, captor);
    }

    public void onUnequippedServer(ServerPlayer player) {
        super.onUnequippedServer(player);
    }

    public void onUnequippedClient(Player player) {
        super.onUnequippedClient(player);
    }

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
    static final ResourceLocation OVERLAY_TEXTURE = ResourceLocation.fromNamespaceAndPath("cuffedaddon", "textures/gui/sleep_mask_overlay.png");

    // VisionBlockingRestraint's seam - see that interface's doc for why
    // this exists (round 24: lets CompactHudRenderer draw the blindfold
    // itself in compact mode, since compact mode never calls
    // renderOverlay() below at all).
    public ResourceLocation getVisionOverlayTexture() {
        return OVERLAY_TEXTURE;
    }

    public void renderOverlay(Player player, GuiGraphics graphics, float partialTick, Window window) {
        int h = window.getGuiScaledHeight();
        int w = window.getGuiScaledWidth();
        // Full-screen blindfold blit, same shape as BundleRestraint's own -
        // this IS the vision-blocking effect, no separate "blindness" logic.
        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        graphics.blit(OVERLAY_TEXTURE, 0, 0, w, h, 0.0f, 0.0f, 32, 18, 32, 18);

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

    public void onKeyInput(Player player, int keyCode, int action) {
        super.onKeyInput(player, keyCode, action);
    }

    public void onMouseInput(Player player, int keyCode, int action) {
        super.onMouseInput(player, keyCode, action);
    }

    @Nonnull
    @OnlyIn(Dist.CLIENT)
    @Override
    public RestraintModelInterface getModelInterface() {
        return new SleepMaskRestraintModelInterface();
    }

    // #endregion

    @OnlyIn(Dist.CLIENT)
    public static class SleepMaskRestraintModelInterface extends RestraintModelInterface {

        @SuppressWarnings("unchecked")
        static final Class<? extends HumanoidModel<? extends LivingEntity>> MODEL_CLASS = (Class<? extends HumanoidModel<? extends LivingEntity>>) (Class<?>) BundleModel.class;
        static final ModelLayerLocation MODEL_LAYER = ModModelLayers.BUNDLE_LAYER;
        static final ResourceLocation MODEL_TEXTURE = ResourceLocation.fromNamespaceAndPath("cuffedaddon", "textures/entity/sleep_mask.png");

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
            return MODEL_TEXTURE;
        }
    }
}
