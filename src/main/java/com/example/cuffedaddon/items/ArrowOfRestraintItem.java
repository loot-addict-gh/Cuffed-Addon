package com.example.cuffedaddon.items;

import java.util.List;

import javax.annotation.Nullable;

import com.example.cuffedaddon.entity.ArrowOfRestraintEntity;
import com.example.cuffedaddon.init.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The Arrow of Restraint - an arrow that carries a whole restraint item inside
 * it and puts that restraint on whoever it hits.
 *
 * <h2>One item, one texture, any restraint</h2>
 * There is a single Arrow of Restraint item. The restraint it will apply is held
 * in its NBT under {@code Restraint} as a <b>complete saved ItemStack</b>, not
 * as a registry id, which is what makes every variant share one texture while
 * still carrying the exact restraint it was crafted from - its enchantments
 * (Repellent, Restraint Gaze, Illusion, and any of Cuffed's own), its damage
 * value, and anything else a future restraint item might store. A Repellent III
 * rope crafted into an arrow applies a Repellent III rope, because the stack
 * that reaches {@code RestraintAPI.getRestraintFromStack} is bit-for-bit the one
 * that went into the crafting grid. Nothing had to be taught about enchantments
 * for that to work - [stated] confirmed it in game at 1.6.0.
 *
 * <p>Two arrows holding the same restraint with the same NBT stack together, as
 * item stacks do; two holding different restraints do not. That falls out of
 * vanilla stacking rules and is exactly the behaviour wanted.
 *
 * <p>Enchanting the ARROW does not enchant the restraint inside it, which
 * [stated] checked and confirmed is the correct behaviour: the payload is a
 * separate stack and nothing copies enchantments across.
 *
 * <h2>Why this extends ArrowItem</h2>
 * Because vanilla {@code BowItem#releaseUsing} ends in
 * {@code arrowitem.createArrow(level, ammo, shooter)}, subclassing
 * {@code ArrowItem} and overriding that one method is enough for
 * {@link ReinforcedBowItem} to need no firing code at all - draw strength,
 * velocity, the full-draw crit, Power, Punch, Flame, bow durability, ammo
 * consumption and creative-mode handling are all vanilla's, unmodified. See
 * {@code ReinforcedBowItem} for the whole argument.
 *
 * <p>Being an {@code ArrowItem} does NOT make it ordinary ammunition: the item
 * is deliberately left out of the {@code minecraft:arrows} tag. Forge's
 * {@code ArrowItem#isInfinite} is also inherited as-is and its stock body is
 * {@code this.getClass() == ArrowItem.class}, so a subclass can never be
 * duplicated by Infinity even if an operator forces Infinity onto a bow.
 *
 * <p>No dispenser behaviour is registered for it either. Vanilla registers the
 * arrow dispense behaviour against {@code Items.ARROW} specifically rather than
 * against the class, so this item is simply not dispensable - which is also why
 * it cannot collide with this addon's restraint dispenser traps (see
 * {@code RestraintTraps}; those are keyed on restraint ITEMS, and an arrow is
 * not one).
 */
public class ArrowOfRestraintItem extends ArrowItem implements ReinforcedBowAmmo {

    /** NBT key on the ARROW stack holding the saved restraint ItemStack. */
    public static final String TAG_RESTRAINT = "Restraint";

    public ArrowOfRestraintItem(Properties properties) {
        super(properties);
    }

    // ---------------------------------------------------------------- NBT

    /**
     * Builds an Arrow of Restraint carrying {@code restraint}.
     *
     * <p>The restraint is copied down to a single item and {@code save}d, which
     * writes a fresh CompoundTag - {@code ItemStack#save} copies, unlike the
     * read direction (see {@link #getRestraint}). So the stack handed in here is
     * never aliased by the arrow afterwards.
     *
     * <p>This is the <b>only</b> place a loaded arrow is built - the crafting
     * recipes, the creative tab and the dropped-arrow pickup all route through
     * it. That matters for JEI: pressing the recipe key on an arrow matches by
     * exact ItemStack, so a creative-tab arrow and a crafted one have to be
     * byte-identical, and they are because they come from the same line of code.
     */
    public static ItemStack withRestraint(ItemStack restraint) {
        ItemStack arrow = new ItemStack(ModItems.ARROW_OF_RESTRAINT.get());
        if (!restraint.isEmpty()) {
            arrow.getOrCreateTag().put(TAG_RESTRAINT, restraint.copyWithCount(1).save(new CompoundTag()));
        }
        return arrow;
    }

    /**
     * The restraint this arrow carries, or an empty stack.
     *
     * <p>The {@code .copy()} on the sub-tag is not optional. {@code ItemStack.of}
     * in 1.20.1 is literally {@code this.tag = tag.getCompound("tag")} - it does
     * NOT copy what it reads, so the returned stack would SHARE its NBT with
     * this arrow's tag, and {@code verifyTagAfterLoad} can mutate it even if we
     * never touch it ourselves. That exact aliasing bug cost a round on the
     * Restraint Gaze copy at 1.5.16; copying first is the standing fix.
     */
    public static ItemStack getRestraint(ItemStack arrow) {
        CompoundTag tag = arrow.getTag();
        if (tag == null || !tag.contains(TAG_RESTRAINT, Tag.TAG_COMPOUND)) {
            return ItemStack.EMPTY;
        }
        return ItemStack.of(tag.getCompound(TAG_RESTRAINT).copy());
    }

    // ------------------------------------------------------------ firing

    /**
     * Called by vanilla {@code BowItem#releaseUsing}. Everything else about the
     * shot has already been or is about to be handled by vanilla around this
     * call; all this does is hand back an entity that remembers the payload.
     */
    @Override
    public AbstractArrow createArrow(Level level, ItemStack ammo, LivingEntity shooter) {
        return new ArrowOfRestraintEntity(level, shooter, getRestraint(ammo));
    }

    // ----------------------------------------------------------- tooltip

    /**
     * Names the contained restraint, then lists its enchantments.
     *
     * <p>Not cosmetic: every Arrow of Restraint looks identical by design, so
     * without this there is no way to tell a handcuffs arrow from a straitjacket
     * arrow in an inventory, and no way to tell an enchanted one from a plain
     * one. The restraint's name is printed bare, with no "Applies:" label -
     * [stated] asked for the label dropped once they had seen it in game.
     *
     * <p>The enchantment lines are read straight out of the stored stack's
     * {@code Enchantments} list rather than through {@code EnchantmentHelper},
     * which keeps this independent of how any particular restraint stores its
     * own data.
     */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        ItemStack restraint = getRestraint(stack);
        if (restraint.isEmpty()) {
            tooltip.add(Component.translatable("item.cuffedaddon.arrow_of_restraint.tooltip.empty")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.add(restraint.getHoverName().copy().withStyle(ChatFormatting.GRAY));

        ListTag enchantments = restraint.getEnchantmentTags();
        for (int i = 0; i < enchantments.size(); i++) {
            CompoundTag entry = enchantments.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(entry.getString("id"));
            if (id == null) {
                continue;
            }
            Enchantment enchantment = ForgeRegistries.ENCHANTMENTS.getValue(id);
            if (enchantment != null) {
                tooltip.add(enchantment.getFullname(entry.getInt("lvl")));
            }
        }
    }
}
