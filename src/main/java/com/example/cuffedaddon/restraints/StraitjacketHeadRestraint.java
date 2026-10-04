package com.example.cuffedaddon.restraints;

import com.example.cuffedaddon.CuffedAddon;
import com.example.cuffedaddon.init.ModItems;
import com.example.cuffedaddon.init.ModRestraints;
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
 * Straitjacket's head restraint. Per [stated]'s explicit request, this is
 * deliberately NOT built as a 5th "combination" head restraint
 * (AbstractComboHeadRestraint/CombinationRestraintRecipe) - the whole point
 * was to get both muffling AND vision-blocking from ONE item/restraint,
 * without adding another combo recipe on top of the existing 4. So this
 * extends AbstractHeadRestraint directly and implements VisionBlockingRestraint
 * itself, following the exact same renderOverlay/getModelInterface shape
 * AbstractComboHeadRestraint uses (full-screen vision overlay blit, then the
 * small chain icon) - just inlined here since there's only one restraint
 * that needs it, not four sharing a base class.
 *
 * Muffling: chat-muted unconditionally, matching Rope and the 4 combos - see
 * ClientEvents.onChat, which checks StraitjacketHeadRestraint.ID.
 *
 * Non-breakable (canBeBrokenOutOf false), same reasoning as RopeHeadRestraint/
 * the 4 combos - no "Straitjacket when on Head" config category needed.
 *
 * Vision overlay: [stated]'s own explicit request - "copy that of the
 * bundle and retexture it gray" - textures/gui/straitjacket_overlay.png is a
 * grayscale (luminance-preserving, alpha untouched) copy of the addon's own
 * bundle_duct_tape_overlay.png (itself a byte-for-byte copy of Cuffed's real
 * bundle overlay - see BundleDuctTapeRestraint/BundleRopeRestraint, which
 * are identical files), rather than Sleep Mask's own overlay.
 *
 * Worn head model: getModelInterface() reuses Cuffed's own BundleModel +
 * already-registered ModModelLayers.BUNDLE_LAYER - the EXACT same geometry
 * Bundle/Sleep Mask/all 4 combos already use for their own head coverage
 * (see AbstractComboHeadRestraint's own ComboModelInterface, which this
 * mirrors) - per [stated]'s explicit request ("wired the same way as the
 * bundle and the other vision blocking head restraints"). An earlier attempt
 * used a custom StraitjacketHeadWrapModel box (0.3f inflate) instead; that
 * sat nowhere near covering the vanilla hat/second-skin layer on the head
 * (which sits further out than the body's own second layer), so it's
 * replaced entirely with the shape that's already proven to cover every
 * other head restraint correctly. Own texture
 * (textures/entity/straitjacket_head.png, 64x64, matching BundleModel's own
 * declared texture size). BundleModel is a single 9x9x9 "sack" box,
 * texOffs(0,0), CubeDeformation(0.0f) - confirmed against both Cuffed's
 * public GitHub source and the real compiled 1.3.15 jar (decompiled with
 * CFR) - standard 6-face box UV unwrap, occupying only x[0,36)/y[0,18) of
 * the 64x64 canvas (top/bottom/right/front/left/back, 9x9 each); the rest
 * of the texture is unused. Placeholder is red ONLY within that region, the
 * rest fully transparent, same treatment as the arms/legs wrap textures.
 */
public class StraitjacketHeadRestraint extends AbstractHeadRestraint implements VisionBlockingRestraint {
    static final ResourceLocation CUFFED_WIDGETS = ResourceLocation.fromNamespaceAndPath("cuffed", "textures/gui/widgets.png");
    private static final ResourceLocation OVERLAY_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CuffedAddon.MODID, "textures/gui/straitjacket_overlay.png");
    private static final ResourceLocation MODEL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CuffedAddon.MODID, "textures/entity/straitjacket_head.png");

    public static final ResourceLocation ID = ModRestraints.STRAITJACKET_HEAD.getId();
    public static final Item ITEM = ModItems.STRAITJACKET.get();

    public StraitjacketHeadRestraint() {
    }

    public StraitjacketHeadRestraint(ItemStack stack, ServerPlayer player, ServerPlayer captor) {
        super(stack, player, captor);
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public String getActionBarLabel() {
        return "info.cuffedaddon.restraints.straitjacket_head.action_bar";
    }

    @Override
    public String getName() {
        return "info.cuffedaddon.restraints.straitjacket_head.name";
    }

    @Override
    public Item getItem() {
        return ITEM;
    }

    /**
     * The Straitjacket needs a HANDCUFFS KEY to come off, as of 1.5.13.
     *
     * <p>It used to be null, which in Cuffed's rules means "any key opens this,
     * and so does a bare hand" - that bare-hand clause is what [stated] asked to
     * remove: "all straitjacket slots should require handcuff keys to be
     * removed, and not be removable by hand". Naming a key item is the whole
     * change; Cuffed's own removal rules then do the rest, in both places they
     * are implemented (RestrainableCapability#onInteractedByOther for real
     * players, FakePlayerRestraintUtil#tryRemove for fake ones), so an empty
     * hand is refused and a shackles key is refused while a handcuffs key works.
     *
     * <p>Resolved lazily rather than cached in a static field. Cuffed can get
     * away with {@code static final Item KEY = ModItems.HANDCUFFS_KEY.get()}
     * because its items and its restraints are registered by the same mod in a
     * known order; ours are a different mod's, and a class initialiser that
     * fires before Cuffed's item registry is populated would throw. A method
     * call has no such ordering to get wrong.
     */
    @Override
    public Item getKeyItem() {
        return com.lazrproductions.cuffed.init.ModItems.HANDCUFFS_KEY.get();
    }

    @Override
    public SoundEvent getEquipSound() {
        return SoundEvents.WOOL_PLACE;
    }

    @Override
    public SoundEvent getUnequipSound() {
        return SoundEvents.WOOL_PLACE;
    }

    @Override
    public boolean AllowBreakingBlocks() {
        return true;
    }

    @Override
    public boolean AllowItemUse() {
        return true;
    }

    @Override
    public boolean AllowMovement() {
        return true;
    }

    @Override
    public boolean AllowJumping() {
        return true;
    }

    @Override
    public boolean AllowSprinting() {
        return true;
    }

    public boolean canBeBrokenOutOf() {
        return false;
    }

    @Override
    public boolean getLockpickable() {
        return false;
    }

    @Override
    public int getLockpickingProgressPerPick() {
        return 5;
    }

    @Override
    public int getLockpickingSpeedIncreasePerPick() {
        return 0;
    }

    @Override
    public ArmRestraintAnimationFlags getArmAnimationFlags() {
        return ArmRestraintAnimationFlags.NONE;
    }

    @Override
    public LegRestraintAnimationFlags getLegAnimationFlags() {
        return LegRestraintAnimationFlags.NONE;
    }

    @Override
    public void onTickServer(ServerPlayer player) {
        super.onTickServer(player);
    }

    @Override
    public void onTickClient(Player player) {
        super.onTickClient(player);
    }

    @Override
    public void onEquippedServer(ServerPlayer player, ServerPlayer captor) {
        super.onEquippedServer(player, captor);
    }

    @Override
    public void onEquippedClient(Player player, Player captor) {
        super.onEquippedClient(player, captor);
    }

    @Override
    public void onUnequippedServer(ServerPlayer player) {
        super.onUnequippedServer(player);
    }

    @Override
    public void onUnequippedClient(Player player) {
        super.onUnequippedClient(player);
    }

    @Override
    public void onLoginServer(ServerPlayer player) {
    }

    @Override
    public void onLoginClient(Player player) {
    }

    @Override
    public void onLogoutServer(ServerPlayer player) {
    }

    @Override
    public void onLogoutClient(Player player) {
    }

    @Override
    public void onDeathServer(ServerPlayer player) {
    }

    @Override
    public void onDeathClient(Player player) {
    }

    @Override
    public void onJumpServer(ServerPlayer player) {
    }

    @Override
    public void onJumpClient(Player player) {
    }

    @Override
    public float onLandServer(ServerPlayer player, float distance, float damageMultiplier) {
        return 1.0f;
    }

    @Override
    public void onLandClient(Player player, float distance, float damageMultiplier) {
    }

    /**
     * Same shape as AbstractComboHeadRestraint's own renderOverlay: full
     * screen vision-blocking blit first, then the shared chain icon.
     */
    @Override
    public void renderOverlay(Player player, GuiGraphics graphics, float partialTick, Window window) {
        int h = window.getGuiScaledHeight();
        int w = window.getGuiScaledWidth();
        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        graphics.blit(getVisionOverlayTexture(), 0, 0, w, h, 0.0f, 0.0f, 32, 18, 32, 18);

        super.renderOverlay(player, graphics, partialTick, window);

        float f = 1.0f;
        graphics.setColor(f, f, f, 1.0f);
        int iconWidth = (int) (16 * 1.75f);
        int iconHeight = (int) (16 * 1.75f);
        int x = (window.getGuiScaledWidth() / 2) - (iconWidth / 2);
        int y = (window.getGuiScaledHeight() / 2) - iconHeight - 100;
        graphics.blit(CUFFED_WIDGETS, x, y, iconWidth, iconHeight, 44.0f, 24.0f, 16, 16, 192, 192);
        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @Override
    public ResourceLocation getVisionOverlayTexture() {
        return OVERLAY_TEXTURE;
    }

    @Override
    public void onKeyInput(Player player, int keyCode, int action) {
        super.onKeyInput(player, keyCode, action);
    }

    @Override
    public void onMouseInput(Player player, int keyCode, int action) {
        super.onMouseInput(player, keyCode, action);
    }

    @Override
    @Nonnull
    @OnlyIn(Dist.CLIENT)
    public RestraintModelInterface getModelInterface() {
        return new StraitjacketHeadRestraintModelInterface();
    }

    @OnlyIn(Dist.CLIENT)
    public static class StraitjacketHeadRestraintModelInterface extends RestraintModelInterface {
        @SuppressWarnings("unchecked")
        static final Class<? extends HumanoidModel<? extends LivingEntity>> MODEL_CLASS =
                (Class<? extends HumanoidModel<? extends LivingEntity>>) (Class<?>) BundleModel.class;
        static final ModelLayerLocation MODEL_LAYER = ModModelLayers.BUNDLE_LAYER;

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
