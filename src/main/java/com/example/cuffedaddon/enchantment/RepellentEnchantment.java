package com.example.cuffedaddon.enchantment;

import com.lazrproductions.cuffed.restraints.RestraintAPI;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

import javax.annotation.Nonnull;

/**
 * Repellent (1.5.15) - pushes hostile mobs away from the restrained wearer.
 *
 * <p>The behaviour itself lives in {@link RepellentEvents}; this class is only
 * the registry object and its obtainability rules.
 *
 * <h2>Obtainable in survival, unlike Restraint Gaze</h2>
 * Everything here mirrors Cuffed's own {@code FamineEnchantment} /
 * {@code ImbueEnchantment} so that Repellent is found, traded and applied by
 * exactly the same routes a player already knows for Famine and Shroud:
 * {@code isDiscoverable()} true puts it in the enchanting table's roll and in
 * {@code EnchantRandomlyFunction} loot, {@code isTradeable()} (inherited true)
 * lets librarians offer it, and {@code canApplyAtEnchantingTable} restricts it
 * to restraint items.
 *
 * <p>In practice the route is always <em>book, then anvil</em>, because no
 * restraint item on either side of the house has a non-zero enchantment value,
 * so a table can never roll this directly onto a restraint - see
 * {@code ModEnchantments}' note on why the anvil half needs a custom
 * EnchantmentCategory rather than BREAKABLE.
 *
 * <h2>The three levels are not simply "stronger"</h2>
 * A level alone does nothing. Level N only takes effect while N of the wearer's
 * restraints carry Repellent at level N or better, so level 3 means all three
 * slots. That rule is in {@code RestraintEnchantmentUtil#effectiveTier} rather
 * than here, because it is a property of the wearer, not of the item.
 */
public class RepellentEnchantment extends Enchantment {

    public RepellentEnchantment(Rarity rarity, EnchantmentCategory category, EquipmentSlot... slots) {
        super(rarity, category, slots);
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public int getMinCost(int level) {
        return 15 + (level - 1) * 9;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 50;
    }

    /**
     * Restraints only - the same check Cuffed's own six use. Note this gates the
     * enchanting TABLE, not the anvil; the anvil goes through the category (see
     * {@code ModEnchantments}).
     */
    @Override
    public boolean canApplyAtEnchantingTable(@Nonnull ItemStack stack) {
        return RestraintAPI.isRestraintItem(stack);
    }

    @Override
    public boolean isDiscoverable() {
        return true;
    }
}
