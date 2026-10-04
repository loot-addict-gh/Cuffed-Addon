package com.example.cuffedaddon.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

import javax.annotation.Nonnull;

/**
 * Restraint Gaze (1.5.15) - stare at another player long enough and the same
 * restraint appears on them.
 *
 * <p>The behaviour lives in {@link RestraintGazeEvents}; this class is the
 * registry object plus, more importantly, the four overrides that keep it out of
 * survival entirely.
 *
 * <h2>Creative-only, and how that is actually enforced</h2>
 * [stated]'s requirement was "not obtainable in survival by any way (enchantment
 * table, villagers, etc)". There is no single flag for that in 1.20.1, so it is
 * three flags, each closing one route, and it is worth knowing which is which if
 * a future Forge/Minecraft version ever moves one:
 *
 * <ul>
 *   <li>{@link #isDiscoverable()} false - closes the enchanting table (Forge's
 *       patched {@code EnchantmentHelper#getAvailableEnchantmentResults} tests
 *       it before anything else) and closes randomly-generated loot, which is
 *       the same predicate {@code EnchantRandomlyFunction} filters on. This is
 *       the one that does most of the work.</li>
 *   <li>{@link #isTradeable()} false - closes villagers.
 *       {@code VillagerTrades.EnchantBookForEmeralds} streams the enchantment
 *       registry filtered on exactly this.</li>
 *   <li>{@link #canApplyAtEnchantingTable(ItemStack)} false - belt and braces
 *       behind {@code isDiscoverable}, and the hook Cuffed's own enchantments
 *       use, so anything that checks the polite question rather than the
 *       registry-wide one also gets a no.</li>
 * </ul>
 *
 * <p>{@link #isTreasureOnly()} is deliberately left false rather than set true.
 * "Treasure" is not a synonym for "uncraftable": treasure enchantments still
 * generate in loot and still trade, they are just excluded from the table, so
 * turning it on would have <em>added</em> a survival route rather than removing
 * one.
 *
 * <p>{@code isAllowedOnBooks()} is left at its default true on purpose. It only
 * governs whether an enchanted book of this may exist at all, which it must -
 * the creative tab hands out exactly that (see {@code ModCreativeTabContent}) -
 * and it never creates a survival route by itself, because every generator that
 * would produce such a book is already gated on {@code isDiscoverable}.
 *
 * <p>What is NOT blocked, on purpose: an admin handing a player a Gaze book, and
 * that player anvilling it onto a restraint in survival. The requirement was
 * that it cannot be <em>obtained</em>, not that it cannot be used, and the
 * anvil path is what makes the creative-tab books useful at all.
 */
public class RestraintGazeEnchantment extends Enchantment {

    public RestraintGazeEnchantment(Rarity rarity, EnchantmentCategory category, EquipmentSlot... slots) {
        super(rarity, category, slots);
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    /**
     * Never actually consulted - the table can't offer this at all - but kept
     * sane rather than left at the base class's defaults so nothing downstream
     * has to cope with a nonsense cost range.
     */
    @Override
    public int getMinCost(int level) {
        return 20 + (level - 1) * 10;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 50;
    }

    @Override
    public boolean canApplyAtEnchantingTable(@Nonnull ItemStack stack) {
        return false;
    }

    @Override
    public boolean isDiscoverable() {
        return false;
    }

    @Override
    public boolean isTradeable() {
        return false;
    }

    @Override
    public boolean isTreasureOnly() {
        return false;
    }
}
