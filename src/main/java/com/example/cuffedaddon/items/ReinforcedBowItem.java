package com.example.cuffedaddon.items;

import java.util.function.Predicate;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The Reinforced Bow (1.6.0) - an ordinary bow in every respect except two:
 * it is the only weapon that can fire a {@link ArrowOfRestraintItem}, and it
 * cannot take Infinity.
 *
 * <h2>Why this subclasses BowItem instead of reimplementing it</h2>
 * Everything about how a bow behaves - the 72000-tick use duration, the
 * three-stage pull, {@code getPowerForTime}, the charge-to-velocity curve,
 * Power/Punch/Flame application, the crit at full draw, one point of durability
 * per shot, the ammo consumption rules, creative mode, Forge's
 * {@code onArrowNock}/{@code onArrowLoose} events - lives in vanilla
 * {@code BowItem#use} and {@code BowItem#releaseUsing}. <b>Neither is
 * overridden here.</b> That is deliberate and it is the whole reason the
 * Restraining Arrow is an {@code ArrowItem} subclass: vanilla's
 * {@code releaseUsing} ends in
 * {@code arrowitem.createArrow(level, ammoStack, player)}, so a custom arrow
 * entity only has to be returned from there and every other rule above is
 * inherited verbatim rather than copied. Copied code is where a bow stops
 * feeling like a bow; there is none of it here.
 *
 * <p>Durability is 384, the same as a vanilla bow. Enchanting works exactly as
 * it does on a vanilla bow for the same structural reason: vanilla's
 * {@code EnchantmentCategory.BOW#canEnchant} tests
 * {@code item instanceof BowItem}, which this is, so Power, Punch, Flame,
 * Unbreaking and Mending all apply at a table or an anvil with no registration
 * of our own. {@code BowItem}'s enchantment value is inherited too, so the cost
 * and the level spread at a table match a vanilla bow's.
 *
 * <h2>The two ways Infinity can reach a bow, and both are closed</h2>
 * There is no vanilla "this item refuses that enchantment" field, and
 * {@code Enchantments.INFINITY_ARROWS} belongs to {@code EnchantmentCategory
 * .BOW}, so by default it would apply here. Forge provides exactly one hook per
 * route and both are implemented below:
 *
 * <ul>
 *   <li><b>Enchanting table.</b> Forge patches {@code EnchantmentHelper} so the
 *       candidate list is filtered through
 *       {@code enchantment.canApplyAtEnchantingTable(stack)}, which delegates to
 *       {@link #canApplyAtEnchantingTable(ItemStack, Enchantment)} on the item.
 *       Returning false there removes Infinity from the table's pool entirely -
 *       it is never offered, so the player never spends levels on it.</li>
 *   <li><b>Anvil, with an enchanted book.</b> Forge patches {@code AnvilMenu}
 *       with {@code if (flag && !result.isBookEnchantable(book)) result =
 *       ItemStack.EMPTY;}, which delegates to
 *       {@link #isBookEnchantable(ItemStack, ItemStack)}. That hook is
 *       all-or-nothing for the whole book, so the test below is narrow on
 *       purpose: only a book that actually CARRIES Infinity is refused. A Power
 *       V book, a Mending book, or a Power+Unbreaking book still combines
 *       normally. An Infinity book simply produces no anvil result, the same
 *       blank output you get from any incompatible pairing.</li>
 * </ul>
 *
 * <p>Both the stored-enchantment list of an enchanted book and the ordinary
 * enchantment list of a tool are read as raw NBT here rather than through
 * {@code EnchantmentHelper}, because the two live under different tags
 * ({@code StoredEnchantments} vs {@code Enchantments}) and the raw {@code id} /
 * {@code lvl} entry format is the one thing about enchantment NBT that has not
 * moved in many versions. The id is compared as a parsed
 * {@link ResourceLocation} so a legacy unnamespaced {@code "infinity"} matches
 * {@code "minecraft:infinity"} too.
 *
 * <p><b>There is no tooltip.</b> 1.6.0 had one naming the arrows and the
 * Infinity refusal; [stated] cut it at 1.6.2 - "remove the reinforced bow
 * tooltip entirely as if I end up adding more arrows, it would just be too
 * large". So the bow says nothing about itself, and what it can fire is learned
 * by trying it or from the recipe book.
 *
 * <p>The remaining route is {@code /enchant}, which is permission-gated and
 * checks only {@code Enchantment#canEnchant}; an operator can still force
 * Infinity on. That is accepted rather than fought - it needs level 2, and it
 * is the same door through which an operator can give themselves anything.
 */
public class ReinforcedBowItem extends BowItem {

    /**
     * Vanilla's bow accepts {@code ItemTags.ARROWS} and nothing else
     * ({@code ProjectileWeaponItem.ARROW_ONLY}). This widens that by exactly one
     * item class, and that one-line difference is the whole "only this bow can
     * fire restraining arrows" rule:
     *
     * <ul>
     *   <li>neither special arrow is added to the
     *       {@code minecraft:arrows} item tag, so every weapon whose ammo
     *       predicate is tag-based rejects it - the vanilla bow, the vanilla
     *       crossbow, and the great majority of modded bows, which copy
     *       {@code ARROW_ONLY} or build their own tag test;</li>
     *   <li>this bow accepts them because of the {@link ReinforcedBowAmmo}
     *       {@code instanceof} below, which is one test however many special
     *       arrows the mod grows.</li>
     * </ul>
     *
     * <p>A modded bow that tests {@code instanceof ArrowItem} instead of the tag
     * would still find them as ammo, which no predicate of ours can prevent.
     * That last gap is closed on the other side, in
     * {@code AbstractReinforcedArrow}: the special effect only fires when a
     * Reinforced Bow actually loosed the shot, and otherwise the arrow does
     * plain arrow damage and can still be picked back up fully loaded. So the
     * worst a foreign bow can do is waste a shot - never apply a restraint,
     * never shock anyone, and never destroy the payload.
     */
    public static final Predicate<ItemStack> REINFORCED_AMMO = stack ->
            stack.is(ItemTags.ARROWS) || stack.getItem() instanceof ReinforcedBowAmmo;

    public ReinforcedBowItem(Properties properties) {
        super(properties);
    }

    @Override
    public Predicate<ItemStack> getAllSupportedProjectiles() {
        return REINFORCED_AMMO;
    }

    /**
     * Blocks Infinity at an enchanting table. Everything else falls through to
     * the stock rule.
     *
     * <p>The fall-through is written out ({@code enchantment.category
     * .canEnchant(...)}) rather than called as {@code super}, because the stock
     * rule is a DEFAULT METHOD on Forge's {@code IForgeItem} rather than an
     * override on {@code Item} or {@code BowItem}. Spelling it out removes any
     * question about how {@code super} resolves through an interface default,
     * and it is a single verified expression - it is the exact body of
     * {@code IForgeItem#canApplyAtEnchantingTable} in Forge for 1.20.1.
     */
    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        if (enchantment == Enchantments.INFINITY_ARROWS) {
            return false;
        }
        return enchantment.category.canEnchant(stack.getItem());
    }

    /** Blocks an Infinity book at an anvil; any other book combines normally. */
    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return !carriesInfinity(book);
    }

    private static boolean carriesInfinity(ItemStack book) {
        if (book.isEmpty()) {
            return false;
        }
        ResourceLocation infinity = ForgeRegistries.ENCHANTMENTS.getKey(Enchantments.INFINITY_ARROWS);
        if (infinity == null) {
            return false;
        }
        ListTag entries = book.is(Items.ENCHANTED_BOOK)
                ? EnchantedBookItem.getEnchantments(book)
                : book.getEnchantmentTags();
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(entry.getString("id"));
            if (infinity.equals(id)) {
                return true;
            }
        }
        return false;
    }
}
