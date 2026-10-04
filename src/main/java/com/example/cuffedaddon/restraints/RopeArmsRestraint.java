package com.example.cuffedaddon.restraints;

import com.example.cuffedaddon.config.CuffedAddonServerConfig;
import com.example.cuffedaddon.init.ModItems;
import com.example.cuffedaddon.init.ModRestraints;
import com.lazrproductions.cuffed.api.CuffedAPI;
import com.lazrproductions.cuffed.cap.base.IRestrainableCapability;
import com.lazrproductions.cuffed.entity.animation.ArmRestraintAnimationFlags;
import com.lazrproductions.cuffed.entity.animation.LegRestraintAnimationFlags;
import com.lazrproductions.cuffed.init.ModModelLayers;
import com.lazrproductions.cuffed.init.ModStatistics;
import com.lazrproductions.cuffed.restraints.base.AbstractArmRestraint;
import com.lazrproductions.cuffed.restraints.base.IBreakableRestraint;
import com.lazrproductions.cuffed.restraints.base.RestraintType;
import com.lazrproductions.cuffed.restraints.client.RestraintModelInterface;
import com.lazrproductions.cuffed.restraints.client.model.DuckTapeArmsModel;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nonnull;
import java.util.Random;

/**
 * Mirrors com.lazrproductions.cuffed.restraints.custom.DuckTapeArmsRestraint
 * almost line for line - same behavior flags, not lockpickable, breaks out on
 * a 50% per-attempt chance with a cooldown. The only real differences are the
 * item/id it points at, reading OUR OWN config instead of Cuffed's, and using
 * a different texture on the same duct-tape model/layer.
 *
 * NOTE: unlike Duck Tape, renderOverlay here does NOT use
 * com.lazrproductions.lazrslib's screen helpers (ScreenUtilities/
 * BlitCoordinates/ScreenTexture) - that library isn't on the addon's compile
 * classpath (it's only a runtime dependency Cuffed itself needs), so this
 * draws the icon and durability bar with plain GuiGraphics calls instead.
 * Visually equivalent, just self-contained.
 */
public class RopeArmsRestraint extends AbstractArmRestraint implements IBreakableRestraint {
    static final ResourceLocation CUFFED_WIDGETS = ResourceLocation.fromNamespaceAndPath("cuffed", "textures/gui/widgets.png");
    static final ResourceLocation CUFFED_PROGRESS_BAR = ResourceLocation.fromNamespaceAndPath("cuffed", "textures/gui/progress_bar.png");
    public static final ResourceLocation ID = ModRestraints.ROPE_ARMS.getId();
    public static final Item ITEM = ModItems.ROPE.get();
    public static final Item KEY = null;
    public static final ArmRestraintAnimationFlags ARM_ANIMATION_FLAGS = ArmRestraintAnimationFlags.ARMS_TIED_BEHIND;
    int lastBarIndex = 0;
    private int durability = 100;
    float breakCooldown = 4.0f;
    int lastKeyPressed = -1;

    public RopeArmsRestraint() {
    }

    public RopeArmsRestraint(ItemStack stack, ServerPlayer player, ServerPlayer captor) {
        super(stack, player, captor);
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public String getActionBarLabel() {
        return "info.cuffedaddon.restraints.rope_arms.action_bar";
    }

    @Override
    public String getName() {
        return "info.cuffedaddon.restraints.rope_arms.name";
    }

    @Override
    public Item getItem() {
        return ITEM;
    }

    @Override
    public Item getKeyItem() {
        return KEY;
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
        return false;
    }

    @Override
    public boolean AllowItemUse() {
        return false;
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
        return ARM_ANIMATION_FLAGS;
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
        if (this.breakCooldown > 0.0f) {
            this.breakCooldown -= 1.0f;
        }
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

    @Override
    public void renderOverlay(Player player, GuiGraphics graphics, float partialTick, Window window) {
        super.renderOverlay(player, graphics, partialTick, window);
        float f = Mth.clamp(this.breakCooldown / 10.0f, 0.0f, 1.0f) + 1.0f;
        graphics.setColor(f, f, f, 1.0f);
        int iconWidth = 28;
        int iconHeight = 28;
        int x = window.getGuiScaledWidth() / 2 - iconWidth / 2;
        int y = window.getGuiScaledHeight() / 2 - iconHeight - 65;
        graphics.blit(CUFFED_WIDGETS, x, y, iconWidth, iconHeight, 44.0f, 24.0f, 16, 16, 192, 192);
        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        float p = Mth.clamp((float) this.clientSidedDurability / (float) this.getMaxDurability(), 0.0f, 1.0f);
        int barY = y + iconHeight - 2;
        // Bottom row: the real progress_bar.png asset's black shadow pixel (row 1,
        // col 0) - tinting doesn't distort this since it's black regardless of
        // multiply. Top row: a plain fill, not the texture, because the texture's
        // only opaque pixel is a FIXED green (38,255,0) - tinting it red/yellow
        // mostly cancels out (red channel starts at 38/255) instead of showing a
        // visible color, so the color row can't come from that pixel.
        graphics.blit(CUFFED_PROGRESS_BAR, x, barY + 1, iconWidth, 1, 0.0f, 1.0f, 1, 1, 2, 2);
        graphics.fill(x, barY, x + iconWidth, barY + 1, 0xFF3B3B3B);
        int filledWidth = Math.round(iconWidth * p);
        if (filledWidth > 0) {
            graphics.fill(x, barY, x + filledWidth, barY + 1, durabilityBarColor(p));
        }
    }

    /**
     * Green at full durability, fading through yellow, down to red as it
     * approaches broken - same red/yellow/green convention as vanilla's item
     * durability bar (and Cuffed's own progress bars), instead of a flat color.
     */
    private static int durabilityBarColor(float p) {
        p = Mth.clamp(p, 0.0f, 1.0f);
        int r, g;
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
        return new RopeArmsRestraintModelInterface();
    }

    @Override
    public SoundEvent getBreakSound() {
        return SoundEvents.LEASH_KNOT_BREAK;
    }

    @Override
    public boolean isKeyToAttemptBreak(int keyCode, Options options) {
        // Arms break via mouse clicks (left/right click), not keyboard - your
        // hands are tied, not your legs. This also keeps arms and legs on
        // completely disjoint input channels (mouse vs keyboard), so both can
        // be worked on simultaneously without one starving the other.
        return keyCode == options.keyAttack.getKey().getValue() || keyCode == options.keyUse.getKey().getValue();
    }

    @Override
    public boolean requireAlternateKeysToAttemptBreak() {
        return true;
    }

    @Override
    public int getMaxDurability() {
        return CuffedAddonServerConfig.ROPE_DURABILITY.get();
    }

    @Override
    public boolean dropItemOnBroken() {
        return CuffedAddonServerConfig.ROPE_ON_ARMS_DROP_ITEM_WHEN_BROKEN.get();
    }

    @Override
    public boolean canBeBrokenOutOf() {
        return CuffedAddonServerConfig.ROPE_ON_ARMS_CAN_BE_BROKEN_OUT_OF.get();
    }

    @Override
    public int getDurability() {
        return this.durability;
    }

    @Override
    public void attemptToBreak(Player player, int keyCode, int action, Options options) {
        if (this.breakCooldown <= 0.0f && this.canBeBrokenOutOf() && this.isKeyToAttemptBreak(keyCode, options)
                && (!this.requireAlternateKeysToAttemptBreak() || keyCode != this.lastKeyPressed)) {
            Random r = new Random();
            double chance = 0.5;
            double cooldownMultiplier = 1.0;
            if (r.nextDouble() < chance) {
                this.lastKeyPressed = keyCode;
                player.playNotifySound(SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1.0f, Mth.randomBetween(player.getRandom(), 0.9f, 1.1f));
                CuffedAPI.Networking.sendRestraintUtilityPacketToServer(this.getType(), 102, -1, false, 0.0, "");
                this.breakCooldown = r.nextInt(20) + Mth.floor(20.0 * cooldownMultiplier);
            }
        }
    }

    @Override
    public void setDurability(ServerPlayer player, int value) {
        if (value > this.getMaxDurability()) {
            value = this.getMaxDurability();
        }
        if (value < 0) {
            value = 0;
        }
        this.durability = value;
        CuffedAPI.Networking.sendRestraintUtilityPacketToClient(player, this.getType(), 101, this.durability, false, 0.0, "");
        if (this.durability <= 0) {
            this.onBrokenServer(player);
        }
    }

    @Override
    public void incrementDurability(ServerPlayer player, int value) {
        int newValue = this.getDurability() + value;
        this.setDurability(player, newValue);
    }

    @Override
    public void onBrokenServer(ServerPlayer player) {
        CuffedAPI.Networking.sendRestraintUtilityPacketToClient(player, this.getType(), 103, 0, false, 0.0, "");
        Random random = new Random();
        player.level().playSound(null, player.blockPosition(), this.getBreakSound(), SoundSource.PLAYERS, 0.8f, random.nextFloat() * 0.2f + 0.9f);
        ModStatistics.awardRestraintBroken(player, this);
        if (this.dropItemOnBroken()) {
            ItemStack stack = this.saveToItemStack();
            stack.setDamageValue(stack.getMaxDamage() - 1);
            ItemEntity e = new ItemEntity(player.level(), player.getX(), player.getY() + 0.6, player.getZ(), stack);
            e.setDefaultPickUpDelay();
            player.level().addFreshEntity((Entity) e);
        }
        IRestrainableCapability cap = CuffedAPI.Capabilities.getRestrainableCapability(player);
        if (this.getType() == RestraintType.Arm) {
            cap.setArmRestraintWithoutWarning(player, null);
        } else {
            cap.setLegRestraintWithoutWarning(player, null);
        }
    }

    @Override
    public void onBrokenClient(Player player) {
    }

    @OnlyIn(Dist.CLIENT)
    public static class RopeArmsRestraintModelInterface extends RestraintModelInterface {
        @SuppressWarnings("unchecked")
        static final Class<? extends HumanoidModel<? extends LivingEntity>> MODEL_CLASS =
                (Class<? extends HumanoidModel<? extends LivingEntity>>) (Class<?>) DuckTapeArmsModel.class;
        static final ModelLayerLocation MODEL_LAYER = ModModelLayers.DUCK_TAPE_ARM_LAYER;
        static final ResourceLocation MODEL_TEXTURE = ResourceLocation.fromNamespaceAndPath("cuffedaddon", "textures/entity/rope.png");

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
