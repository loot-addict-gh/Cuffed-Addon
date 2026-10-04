package com.example.cuffedaddon.enchantment;

import com.lazrproductions.cuffed.restraints.RestraintAPI;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

import javax.annotation.Nonnull;

/**
 * Illusion (1.5.18) - the restraint holds you exactly as tightly as it looks,
 * which is to say not at all.
 *
 * <p>The behaviour is spread across {@link IllusionUtil} and four mixins; this
 * class is the registry object and its obtainability, which is the part [stated]
 * was specific about.
 *
 * <h2>As rare as Mending, because it IS Mending's rules</h2>
 * [stated] asked for "the rarest chance possible, equivalent to mending", and
 * confirmed they meant Mending's actual obtainability rather than just its
 * rarity tier. So this is a copy of {@code MendingEnchantment}'s shape:
 * {@code Rarity.RARE}, max level 1, {@code getMinCost} of {@code level * 25}, and
 * critically {@link #isTreasureOnly()} true.
 *
 * <p>Treasure-only is what makes it Mending-rare rather than merely rare. The
 * enchanting table calls {@code EnchantmentHelper.selectEnchantment} with
 * {@code allowTreasure = false}, and the first thing Forge's patched
 * {@code getAvailableEnchantmentResults} tests is
 * {@code !isTreasureOnly() || allowTreasure} - so a table can never roll this,
 * not even onto a book. What remains is exactly what remains for Mending:
 * librarian trades (which filter on {@code isTradeable()}, left at its default
 * true), chest and fishing loot (which filter on {@code isDiscoverable()}, also
 * left true), and the treasure-enabled loot functions.
 *
 * <p>Note the practical consequence, since it differs from Repellent: there is no
 * "grind a table until it appears" route. A survival player finds an Illusion
 * book or buys one, then anvils it onto a restraint.
 *
 * <h2>Restraints only</h2>
 * The category is this addon's own {@code ModEnchantments.RESTRAINTS}, the
 * predicate over {@code RestraintAPI.isRestraintItem} - which is what gates the
 * anvil, and is the only reason this can go on Rope or a Straitjacket at all (see
 * that class for why vanilla's BREAKABLE category would have refused every
 * restraint this addon adds). {@code canApplyAtEnchantingTable} is overridden to
 * the same check for consistency, though nothing can reach it while the
 * enchantment is treasure-only.
 */
public class IllusionEnchantment extends Enchantment {

    public IllusionEnchantment(Rarity rarity, EnchantmentCategory category, EquipmentSlot... slots) {
        super(rarity, category, slots);
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    /** Mending's own cost curve, for the same reason the rest of this is. */
    @Override
    public int getMinCost(int level) {
        return level * 25;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 50;
    }

    /**
     * The flag that makes this Mending-rare. See the class doc - it is what
     * removes the enchanting table as a route entirely.
     */
    @Override
    public boolean isTreasureOnly() {
        return true;
    }

    @Override
    public boolean canApplyAtEnchantingTable(@Nonnull ItemStack stack) {
        return RestraintAPI.isRestraintItem(stack);
    }
}
