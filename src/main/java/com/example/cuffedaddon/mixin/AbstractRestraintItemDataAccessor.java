package com.example.cuffedaddon.mixin;

import com.lazrproductions.cuffed.restraints.base.AbstractRestraint;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import javax.annotation.Nullable;

/**
 * Reads {@code AbstractRestraint}'s private {@code itemData} - the saved
 * ItemStack NBT of the item a restraint was applied from.
 *
 * <h2>Why this is needed at all</h2>
 * Cuffed only exposes a restraint's enchantments through
 * {@code IEnchantableRestraint}, and <b>only its three metal restraints
 * implement it</b> (HandcuffsArms/Legs, ShacklesArms/Legs, FuzzyHandcuffs).
 * Every head restraint in the game, Cuffed's own duck tape and bundle included,
 * and every one of this addon's eleven restraints, does not - so for those there
 * is no supported way to ask "what is this wearing". That would have ruled out
 * the head slot entirely, and [stated]'s own example for Repellent level 2 was
 * "head and legs".
 *
 * <p>Enchantments DO survive on those restraints regardless: the
 * {@code AbstractRestraint(ItemStack, ServerPlayer, ServerPlayer)} constructor
 * saves the whole stack into {@code itemData}, {@code serializeNBT} writes it
 * out, and {@code saveToItemStack} restores it - which is exactly why unlocking
 * an enchanted rope hands back an enchanted rope today. The data is there; only
 * the getter is missing. This adds one.
 *
 * <h2>Why an accessor rather than calling saveToItemStack()</h2>
 * {@code saveToItemStack()} is public and would have worked, but it is the wrong
 * tool twice over. It allocates a fresh ItemStack (parsing NBT) on every call,
 * and Repellent asks this question for three slots on every restrained player
 * several times a second. Worse, if {@code itemData} is ever unusable,
 * {@code ItemStack.of} hands back the shared {@code ItemStack.EMPTY} singleton
 * and {@code saveToItemStack} then calls {@code setCount}/{@code setDamageValue}
 * straight onto it - mutating a global. Reading the tag lets
 * {@code RestraintEnchantmentUtil} scan the enchantment list in place, with no
 * allocation and nothing to corrupt.
 *
 * <h2>No refmap entry</h2>
 * {@code AbstractRestraint} is another MOD's class, and mods are not obfuscated
 * in production Forge - only vanilla is - so neither the class nor the field
 * needs an SRG name and nothing goes in mixins.cuffedaddon.refmap.json.
 * {@code remap = false} says so explicitly. An interface mixin carrying only
 * {@code @Accessor}s is fine on Mixin 0.8.5; it is {@code @Inject} into an
 * interface's own default method that this version cannot do (see the Player
 * Picker notes).
 */
@Mixin(value = AbstractRestraint.class, remap = false)
public interface AbstractRestraintItemDataAccessor {

    /**
     * The saved ItemStack NBT of the item this restraint came from, in the shape
     * {@code ItemStack#save} writes: {@code id}/{@code Count} at the root and the
     * item's own NBT (enchantments included) under {@code tag}.
     *
     * <p>Null for a restraint that is still the pristine registry instance and
     * has been neither constructed from a stack nor deserialized - which no worn
     * restraint ever is, but callers should not bet on it.
     */
    @Nullable
    @Accessor("itemData")
    CompoundTag cuffedaddon$getItemData();
}
