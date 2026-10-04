package com.example.cuffedaddon.enchantment;

import com.example.cuffedaddon.fakeplayer.IFakeRestrained;
import com.example.cuffedaddon.mixin.AbstractRestraintItemDataAccessor;
import com.lazrproductions.cuffed.cap.base.IRestrainableCapability;
import com.lazrproductions.cuffed.restraints.base.AbstractRestraint;
import com.lazrproductions.cuffed.restraints.base.IEnchantableRestraint;
import com.lazrproductions.cuffed.restraints.base.RestraintType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * Reads this addon's enchantments off whatever a player (or a fake player) is
 * actually wearing, and turns three per-slot levels into one effective tier.
 *
 * <p>Everything here is server-side and read-only, and holds no state of its own:
 * the level is always re-read from what is actually worn rather than cached at
 * equip time. That was originally forced on us - worn restraints used to be a
 * shared registry singleton, so nothing per-restraint could be trusted - and it is
 * kept now that {@code RestraintAPIFreshInstanceMixin} has fixed that, because
 * re-reading is cheap (a tag scan, no allocation) and cannot go stale when a
 * restraint is enchanted, swapped or disenchanted underneath us.
 */
public final class RestraintEnchantmentUtil {

    private RestraintEnchantmentUtil() {
    }

    /** The three ordinary restraint slots, in a fixed order for iteration. */
    public static final RestraintType[] SLOTS = {RestraintType.Head, RestraintType.Arm, RestraintType.Leg};

    // ------------------------------------------------------------- one slot

    /**
     * The level of {@code enchantment} on one worn restraint, or 0.
     *
     * <p>Two sources, in order of authority:
     *
     * <ol>
     *   <li>{@code IEnchantableRestraint#getEnchantments()} - the list Cuffed
     *       itself reads in {@code AbstractRestraint#onTickServer} and mutates
     *       through {@code enchant(...)}. Only Cuffed's three metal restraints
     *       implement it, but where it exists it is the authority, so Repellent
     *       on a pair of handcuffs behaves identically to Famine on the same
     *       pair.</li>
     *   <li>Otherwise the saved item NBT, via
     *       {@link AbstractRestraintItemDataAccessor}. This is the path every
     *       head restraint and all eleven of this addon's restraints take - see
     *       that accessor for why the supported route does not cover them.</li>
     * </ol>
     *
     * <p>Both are plain tag scans: no ItemStack is allocated, so this is cheap
     * enough to call three times per restrained player per tick.
     */
    public static int levelOn(@Nullable AbstractRestraint restraint, Enchantment enchantment) {
        if (restraint == null) {
            return 0;
        }
        if (restraint instanceof IEnchantableRestraint enchantable) {
            return scan(enchantable.getEnchantments(), enchantment);
        }
        if (!(restraint instanceof AbstractRestraintItemDataAccessor accessor)) {
            // Can only happen if the accessor mixin failed to apply, which Mixin
            // would already have shouted about at startup ("required": true).
            return 0;
        }
        CompoundTag itemData = accessor.cuffedaddon$getItemData();
        if (itemData == null) {
            return 0;
        }
        return scan(itemData.getCompound("tag").getList("Enchantments", Tag.TAG_COMPOUND), enchantment);
    }

    /**
     * The level of {@code enchantment} on a loose stack - used for fake players,
     * whose restraint state is the stack itself rather than an
     * {@code AbstractRestraint} (see {@code IFakeRestrained#getStack}).
     */
    public static int levelOn(ItemStack stack, Enchantment enchantment) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        return scan(stack.getEnchantmentTags(), enchantment);
    }

    /**
     * Walks a vanilla {@code Enchantments} list tag, whose entries are
     * {@code {id: "namespace:path", lvl: <short>}}.
     *
     * <p>The id is compared as the plain string vanilla writes rather than being
     * parsed back into a ResourceLocation - fewer moving parts, and
     * {@code getInt} copes with the level being stored as a short.
     */
    private static int scan(@Nullable ListTag enchantments, Enchantment enchantment) {
        if (enchantments == null || enchantments.isEmpty()) {
            return 0;
        }
        ResourceLocation key = ForgeRegistries.ENCHANTMENTS.getKey(enchantment);
        if (key == null) {
            return 0;
        }
        String wanted = key.toString();
        for (int i = 0; i < enchantments.size(); i++) {
            CompoundTag entry = enchantments.getCompound(i);
            if (wanted.equals(entry.getString("id"))) {
                return entry.getInt("lvl");
            }
        }
        return 0;
    }

    // ------------------------------------------------------- whole wearer

    /** The level of {@code enchantment} on the restraint in one slot, or 0. */
    public static int levelInSlot(@Nullable IRestrainableCapability cap, RestraintType slot,
                                  Enchantment enchantment) {
        return cap == null ? 0 : levelOn(cap.getRestraint(slot), enchantment);
    }

    /** The highest level of {@code enchantment} across all three slots, or 0. */
    public static int highestLevel(@Nullable IRestrainableCapability cap, Enchantment enchantment) {
        if (cap == null) {
            return 0;
        }
        int best = 0;
        for (RestraintType slot : SLOTS) {
            best = Math.max(best, levelOn(cap.getRestraint(slot), enchantment));
        }
        return best;
    }

    /** The highest level of {@code enchantment} across a fake player's slots, or 0. */
    public static int highestLevel(@Nullable IFakeRestrained cap, Enchantment enchantment) {
        if (cap == null) {
            return 0;
        }
        int best = 0;
        for (RestraintType slot : SLOTS) {
            best = Math.max(best, levelOn(cap.getStack(slot), enchantment));
        }
        return best;
    }

    // ------------------------------------------------------------ stripping

    /**
     * Removes one enchantment from a stack's NBT, in place.
     *
     * <p>Used for the "Restraint Gaze Chain = false" case: the copy keeps
     * every other enchantment it had and loses only this one, so a chain stops at
     * one hop without otherwise weakening the restraint.
     *
     * <p>Written against the NBT rather than
     * {@code EnchantmentHelper.setEnchantments(...)} because that helper
     * round-trips the whole list through a Map and rewrites it, which would
     * reorder and renormalise enchantments this addon has no business touching.
     * If the list ends up empty the key is removed entirely, so the copy stops
     * showing a glint rather than carrying an empty {@code Enchantments} list.
     */
    public static void stripEnchantment(ItemStack stack, Enchantment enchantment) {
        if (stack.isEmpty()) {
            return;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("Enchantments", Tag.TAG_LIST)) {
            return;
        }
        ResourceLocation key = ForgeRegistries.ENCHANTMENTS.getKey(enchantment);
        if (key == null) {
            return;
        }
        String wanted = key.toString();
        ListTag enchantments = tag.getList("Enchantments", Tag.TAG_COMPOUND);
        for (int i = enchantments.size() - 1; i >= 0; i--) {
            if (wanted.equals(enchantments.getCompound(i).getString("id"))) {
                enchantments.remove(i);
            }
        }
        if (enchantments.isEmpty()) {
            tag.remove("Enchantments");
        }
    }

    // -------------------------------------------------------------- tiers

    /**
     * Repellent's effective tier for a real player.
     */
    public static int repellentTier(@Nullable IRestrainableCapability cap, Enchantment repellent) {
        if (cap == null) {
            return 0;
        }
        return effectiveTier(
                levelOn(cap.getRestraint(RestraintType.Head), repellent),
                levelOn(cap.getRestraint(RestraintType.Arm), repellent),
                levelOn(cap.getRestraint(RestraintType.Leg), repellent));
    }

    /** Repellent's effective tier for a fake player. */
    public static int repellentTier(@Nullable IFakeRestrained cap, Enchantment repellent) {
        if (cap == null) {
            return 0;
        }
        return effectiveTier(
                levelOn(cap.getStack(RestraintType.Head), repellent),
                levelOn(cap.getStack(RestraintType.Arm), repellent),
                levelOn(cap.getStack(RestraintType.Leg), repellent));
    }

    /**
     * [stated]'s gating rule for Repellent, in one place: <b>tier N requires N
     * restraints enchanted to at least level N.</b>
     *
     * <p>"outside of the existence of 3 levels I also want another condition: for
     * level 2 to actually take effect you need to be wearing 2 restraints with
     * level 2 (e.g. head and legs). the equivalent for level 3."
     *
     * <p>Three consequences worth being explicit about, because they all follow
     * from that one sentence rather than being separate decisions:
     *
     * <ul>
     *   <li><b>Tier 3 means all three slots.</b> There are exactly three
     *       enchantable restraint slots (head, arms, legs), so 20 blocks and boss
     *       repelling are an all-or-nothing loadout. The Shock Collar is this
     *       addon's own fourth slot and is not one of Cuffed's restraints, so it
     *       cannot carry a restraint enchantment and cannot be the third
     *       piece.</li>
     *   <li><b>A higher level counts toward a lower requirement.</b> Two
     *       Repellent III restraints are two restraints "with level 2", so they
     *       give tier 2 rather than nothing - the enchantment degrades gracefully
     *       instead of switching off when you lose a piece.</li>
     *   <li><b>Three Repellent I restraints are still tier 1.</b> Quantity never
     *       substitutes for level; the condition is an extra requirement on top
     *       of the level, not an alternative to it.</li>
     * </ul>
     *
     * @return 0 (inactive) through 3
     */
    public static int effectiveTier(int headLevel, int armsLevel, int legsLevel) {
        for (int tier = 3; tier >= 1; tier--) {
            int qualifying = 0;
            if (headLevel >= tier) {
                qualifying++;
            }
            if (armsLevel >= tier) {
                qualifying++;
            }
            if (legsLevel >= tier) {
                qualifying++;
            }
            if (qualifying >= tier) {
                return tier;
            }
        }
        return 0;
    }
}
