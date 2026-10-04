package com.example.cuffedaddon.init;

import com.example.cuffedaddon.enchantment.IllusionEnchantment;
import com.example.cuffedaddon.enchantment.RepellentEnchantment;
import com.example.cuffedaddon.enchantment.RestraintGazeEnchantment;
import com.lazrproductions.cuffed.restraints.RestraintAPI;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * This addon's own restraint enchantments (1.5.15). Cuffed already has an
 * enchantment system and these deliberately plug into it rather than inventing
 * a parallel one - see {@code com.lazrproductions.cuffed.init.ModEnchantments}
 * for its six (Imbue, Famine, Shroud, Exhaust, Silence, Buoyant) and
 * {@code AbstractRestraint#onTickServer} for where it reads them.
 *
 * <h2>Why these need their own EnchantmentCategory</h2>
 * Every one of Cuffed's own enchantments is registered under the vanilla
 * {@link EnchantmentCategory#BREAKABLE} category, which is literally
 * "{@code item.canBeDepleted()}" - the item must have a durability bar. That
 * works for Cuffed because its three metal restraints are all registered with
 * {@code .durability(999)}. It does NOT work here: <b>not one of this addon's
 * restraint items has durability</b> (Rope, Straitjacket, Sleep Mask, the four
 * combos, Bed Restraint and Wall Restraint are all plain
 * {@code new Item.Properties()}), because their durability is a restraint-side
 * config value rather than an item-side damage bar. Under BREAKABLE,
 * {@code Enchantment#canEnchant} would have returned false for all of them and
 * an anvil would simply have refused the book - which would have made
 * "2 restraints with level 2, e.g. head and legs" impossible, since every head
 * restraint in the game except Cuffed's duck tape is one of ours.
 *
 * <p>So {@link #RESTRAINTS} is a Forge extensible-enum category whose predicate
 * is Cuffed's own {@code RestraintAPI.isRestraintItem} - i.e. "is this item
 * registered as a restraint for any of the three slots", ours and Cuffed's
 * alike, plus any other addon's. {@code EnchantmentCategory.create(String,
 * Predicate<Item>)} is a Forge addition (net.minecraftforge.common
 * .IExtensibleEnum), verified present on the MinecraftForge 1.20.1 branch in
 * patches/minecraft/.../EnchantmentCategory.java.patch - it is NOT vanilla API,
 * so don't go looking for it in Mojang's source.
 *
 * <p>Being a category rather than just an override of
 * {@code canApplyAtEnchantingTable} matters because those two hooks gate
 * different things in 1.20.1: {@code AnvilMenu} asks
 * {@code Enchantment#canEnchant(stack)} (the category), while the enchanting
 * TABLE asks {@code canApplyAtEnchantingTable} via Forge's patch to
 * {@code EnchantmentHelper#getAvailableEnchantmentResults}. A restraint is only
 * ever enchanted on an anvil in practice, because none of the restraint items
 * on either side of the house has a non-zero enchantment VALUE, so the table
 * can only ever put these onto a book.
 *
 * <h2>Equipment slot</h2>
 * {@code EquipmentSlot.MAINHAND} is copied from Cuffed's own six for
 * consistency, and is inert either way: a restraint is never in a vanilla
 * equipment slot, so nothing in the game ever walks the slot list looking for
 * these. Both enchantments are read explicitly off the worn restraint instead -
 * see {@code RestraintEnchantmentUtil}.
 */
public class ModEnchantments {

    /**
     * Any item Cuffed knows about as a restraint, for any of the three slots.
     *
     * <p>Evaluated lazily (only when something actually asks whether an
     * enchantment can go on an item), which is what makes it safe to build this
     * predicate at class-init time: {@code RestraintAPI}'s registry list is not
     * populated with this addon's restraints until
     * {@code CuffedAddon#commonSetup}, long before any anvil exists.
     */
    public static final EnchantmentCategory RESTRAINTS = EnchantmentCategory.create(
            "CUFFEDADDON_RESTRAINTS", item -> RestraintAPI.isRestraintItem(new ItemStack(item)));

    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, "cuffedaddon");

    public static final RegistryObject<Enchantment> REPELLENT = ENCHANTMENTS.register("repellent",
            () -> new RepellentEnchantment(Enchantment.Rarity.VERY_RARE, RESTRAINTS, EquipmentSlot.MAINHAND));

    public static final RegistryObject<Enchantment> RESTRAINT_GAZE = ENCHANTMENTS.register("restraint_gaze",
            () -> new RestraintGazeEnchantment(Enchantment.Rarity.VERY_RARE, RESTRAINTS, EquipmentSlot.MAINHAND));

    /**
     * Illusion (1.5.18). Rarity.RARE rather than VERY_RARE deliberately - it is
     * Mending's tier, and Mending's rarity is not what makes Mending rare;
     * {@code isTreasureOnly()} is. See IllusionEnchantment.
     */
    public static final RegistryObject<Enchantment> ILLUSION = ENCHANTMENTS.register("illusion",
            () -> new IllusionEnchantment(Enchantment.Rarity.RARE, RESTRAINTS, EquipmentSlot.MAINHAND));

    public static void register(IEventBus modEventBus) {
        ENCHANTMENTS.register(modEventBus);
    }
}
