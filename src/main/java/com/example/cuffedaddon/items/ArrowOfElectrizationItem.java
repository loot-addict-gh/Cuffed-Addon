package com.example.cuffedaddon.items;

import java.util.List;

import javax.annotation.Nullable;

import com.example.cuffedaddon.collar.ShockCollarUtil;
import com.example.cuffedaddon.entity.ArrowOfElectrizationEntity;
import com.example.cuffedaddon.init.ModEffects;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The Arrow of Electrization - a shock in arrow form.
 *
 * <p>Same shape as {@link ArrowOfRestraintItem} in every structural way: an
 * {@code ArrowItem} subclass so vanilla's {@code BowItem#releaseUsing} drives
 * the shot through {@code createArrow}, kept out of the
 * {@code minecraft:arrows} tag so only the Reinforced Bow can load it, and
 * carrying no state of its own.
 *
 * <p>It carries no NBT at all, which is the one real difference: an Arrow of
 * Restraint has to remember <i>which</i> restraint, while every Arrow of
 * Electrization does the same thing. So it stacks normally, it is one plain
 * vanilla shaped recipe rather than one per variant, and JEI picks that recipe
 * up with nothing special done for it.
 *
 * <p>The effect itself, including the duration and why the nausea arrives when
 * it does, lives in {@link ShockCollarUtil#electrify}.
 */
public class ArrowOfElectrizationItem extends ArrowItem implements ReinforcedBowAmmo {

    public ArrowOfElectrizationItem(Properties properties) {
        super(properties);
    }

    @Override
    public AbstractArrow createArrow(Level level, ItemStack ammo, LivingEntity shooter) {
        return new ArrowOfElectrizationEntity(level, shooter);
    }

    /**
     * The same one-line effect summary a vanilla tipped arrow shows.
     *
     * <p>[stated] at 1.6.2: "I'd like the electrization arrow description be
     * similar to every other tipped arrow (in color and duration showing)".
     * Vanilla builds that line in {@code PotionUtils#addPotionTooltip} as the
     * effect's own translated name, a space, the duration in brackets as
     * {@code MM:SS}, the whole thing coloured by
     * {@code MobEffectCategory#getTooltipFormatting()}.
     *
     * <p>Reproduced rather than called: vanilla's helper wants a list of
     * {@code MobEffectInstance}s read out of a potion's NBT, and this arrow
     * stores nothing - every one of them does the same thing, and the duration
     * lives in the config. Both halves of the formatting are spelled out:
     *
     * <ul>
     *   <li><b>Red</b> is hardcoded because Electrization is registered
     *       {@code MobEffectCategory.HARMFUL} (see {@code ModEffects}) and that
     *       category's tooltip formatting IS red - the same red an Arrow of
     *       Weakness prints its line in.</li>
     *   <li><b>{@code %02d:%02d}</b> matches what vanilla actually renders -
     *       an Arrow of Weakness reads "Weakness (00:30)", minutes padded.</li>
     * </ul>
     *
     * <p>The name comes from the effect's own description id, so the tooltip
     * and the status-effect list can never disagree, and a translation of one
     * is a translation of both. The duration comes from
     * {@code ShockCollarUtil.arrowElectrizationSeconds()}, so the tooltip can
     * never disagree with what the arrow does either.
     *
     * <p>No "When Applied:" block follows it, and that is correct rather than
     * missing: vanilla prints those from the effect's own attribute modifiers,
     * and Electrization declares none - its slowness, weakness and mining
     * fatigue are real sub-effects applied every tick instead (see
     * {@code ElectrizationEffect} for why).
     */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        int seconds = ShockCollarUtil.arrowElectrizationSeconds();
        tooltip.add(Component.translatable(ModEffects.ELECTRIZATION.get().getDescriptionId())
                .append(String.format(" (%02d:%02d)", seconds / 60, seconds % 60))
                .withStyle(ChatFormatting.RED));
    }
}
