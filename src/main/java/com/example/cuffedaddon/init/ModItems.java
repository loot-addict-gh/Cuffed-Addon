package com.example.cuffedaddon.init;

import com.example.cuffedaddon.items.BedRestraintItem;
import com.example.cuffedaddon.items.BlockLockerItem;
import com.example.cuffedaddon.items.BundleDuctTapeItem;
import com.example.cuffedaddon.items.BundleRopeItem;
import com.example.cuffedaddon.items.PlayerPickerItem;
import com.example.cuffedaddon.items.ArrowOfElectrizationItem;
import com.example.cuffedaddon.items.ArrowOfRestraintItem;
import com.example.cuffedaddon.items.ReinforcedBowItem;
import com.example.cuffedaddon.items.RopeItem;
import com.example.cuffedaddon.items.SleepMaskDuctTapeItem;
import com.example.cuffedaddon.items.SleepMaskItem;
import com.example.cuffedaddon.items.ShockCollarItem;
import com.example.cuffedaddon.items.SleepMaskRopeItem;
import com.example.cuffedaddon.items.StraitjacketItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.LinkedHashMap;
import java.util.Map;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "cuffedaddon");

    public static final RegistryObject<Item> ROPE = ITEMS.register("rope", () -> new RopeItem(new Item.Properties()));

    // Rebuilt on top of the pose.LiePose foundation (round 10) - the old
    // (deleted) version was a genuine Cuffed AbstractRestraintItem; this one
    // is a standalone trigger item instead, see BedRestraintItem's own doc.
    public static final RegistryObject<Item> BED_RESTRAINT =
            ITEMS.register("bed_restraint", () -> new BedRestraintItem(new Item.Properties()));

    // 1.3.x - head-only restraint item, works like Bundle (vision block).
    // See SleepMaskItem's own doc for why it extends AbstractHeadRestraintItem
    // directly instead of mirroring Rope's "ambiguous" item pattern.
    public static final RegistryObject<Item> SLEEP_MASK =
            ITEMS.register("sleep_mask", () -> new SleepMaskItem(new Item.Properties()));

    // 1.3.x - combination head restraints: a vision item + a speech item
    // combined in the crafting grid, applying both effects from one item.
    // See AbstractComboHeadRestraint for the shared restraint behavior and
    // ModRecipes for the empty-bundle-required crafting recipes.
    public static final RegistryObject<Item> BUNDLE_DUCT_TAPE =
            ITEMS.register("bundle_duct_tape", () -> new BundleDuctTapeItem(new Item.Properties()));
    public static final RegistryObject<Item> BUNDLE_ROPE =
            ITEMS.register("bundle_rope", () -> new BundleRopeItem(new Item.Properties()));
    public static final RegistryObject<Item> SLEEP_MASK_DUCT_TAPE =
            ITEMS.register("sleep_mask_duct_tape", () -> new SleepMaskDuctTapeItem(new Item.Properties()));
    public static final RegistryObject<Item> SLEEP_MASK_ROPE =
            ITEMS.register("sleep_mask_rope", () -> new SleepMaskRopeItem(new Item.Properties()));

    // 1.3.x - parked idea, item shell only (no functionality yet) - see
    // BlockLockerItem's own doc.
    public static final RegistryObject<Item> BLOCK_LOCKER =
            ITEMS.register("block_locker", () -> new BlockLockerItem(new Item.Properties().durability(16)));

    // 1.3.x - one BlockItem per Reinforced Bed color, matching how vanilla
    // registers a separate item per colored bed. Stacks to 1, same as
    // Cuffed's own "bunk" item (BUNK_ITEM in Cuffed's ModItems).
    public static final Map<String, RegistryObject<Item>> REINFORCED_BED_ITEMS = new LinkedHashMap<>();
    static {
        for (String color : ModBlocks.BED_COLORS) {
            RegistryObject<net.minecraft.world.level.block.Block> block = ModBlocks.REINFORCED_BEDS.get(color);
            REINFORCED_BED_ITEMS.put(color, ITEMS.register("bunk_" + color,
                    () -> new BlockItem(block.get(), new Item.Properties().stacksTo(1))));
        }
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    // Wall Restraint - plain BlockItem, no custom placement class needed
    // (all placement/pairing logic lives on WallRestraintBlock itself, same
    // as vanilla BedItem needs no custom Item class either).
    public static final RegistryObject<Item> WALL_RESTRAINT = ITEMS.register("wall_restraint",
            () -> new BlockItem(ModBlocks.WALL_RESTRAINT.get(), new Item.Properties()));

    // Player Picker - stacksTo(1): it's a reusable single-capacity capture
    // slot, not a stack of identical charges (two loaded pickers would
    // never share NBT anyway, but an EMPTY picker sitting next to another
    // empty one shouldn't silently merge into a stack of 2 either, given
    // how central "the one item in your hand" is to this feature).
    //
    // fireResistant() as of 1.4.40, same property netherite gear carries:
    // the item survives lava and fire instead of burning up. [stated] asked
    // for this after discovering that destroying a LOADED picker left the
    // captured player with no way out at all - the item is the only handle on
    // them, so it must not be destroyable by the most common accident in the
    // game. It is not the whole answer (see /unpick and the grace-period
    // auto-release, both added the same round, for the cases fire-proofing
    // can't cover - void, /kill @e, despawn), but it removes the likeliest one.
    public static final RegistryObject<Item> PLAYER_PICKER = ITEMS.register("player_picker",
            () -> new PlayerPickerItem(new Item.Properties().stacksTo(1).fireResistant()));

    // Straitjacket - "ambiguous" restraint item, same as Rope: applicable on
    // legs, arms, and head from the one item, per [stated]'s explicit
    // request to avoid a combo item. See StraitjacketItem's own doc.
    public static final RegistryObject<Item> STRAITJACKET =
            ITEMS.register("straitjacket", () -> new StraitjacketItem(new Item.Properties()));

    // --- Shock Collar line (1.4.32) ---------------------------------------
    // Three items, per [stated]'s spec: two crafting components that combine
    // (shapeless) into the real item.
    //
    // UNBOUND_COLLAR and SHOCK_REMOTE are deliberately plain Items with no
    // behaviour class of their own - they are ingredients, nothing more. All
    // the actual behaviour lives on SHOCK_COLLAR, which is ONE item with two
    // states: unbound (its own texture/name, applies a collar on right-click)
    // and bound (the remote's texture, the wearer's name in italics, shocks on
    // held right-click). See ShockCollarItem for the whole design.
    public static final RegistryObject<Item> UNBOUND_COLLAR =
            ITEMS.register("unbound_collar", () -> new Item(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> SHOCK_REMOTE =
            ITEMS.register("shock_remote", () -> new Item(new Item.Properties().stacksTo(1)));

    // stacksTo(1) for the same reason PLAYER_PICKER is: a bound one carries
    // per-collaring NBT that must never merge with another, and an unbound one
    // sitting next to another unbound one shouldn't silently stack either,
    // given "the one item in your hand" is central to how it's used.
    public static final RegistryObject<Item> SHOCK_COLLAR =
            ITEMS.register("shock_collar", () -> new ShockCollarItem(new Item.Properties().stacksTo(1)));
    // --- Reinforced Bow / special arrow line (1.6.0) -----------------------
    //
    // durability(384) is the vanilla bow's exact durability, per [stated]'s
    // "The durability should be the same as the vanilla bow". Nothing else is
    // set: no stacksTo (a 384-durability item is already unstackable), no
    // fireResistant, no rarity - the brief was "work just like a normal
    // vanilla bow" and every property beyond durability is inherited from
    // BowItem. Infinity is refused by the ITEM (two Forge hooks on
    // ReinforcedBowItem), not by anything here.
    public static final RegistryObject<Item> REINFORCED_BOW = ITEMS.register("reinforced_bow",
            () -> new ReinforcedBowItem(new Item.Properties().durability(384)));

    // Both arrows take plain Item.Properties: they stack to 64 like any arrow.
    // Two Arrows of Restraint only stack with each other when they carry the
    // same restraint with the same NBT, which is ordinary ItemStack behaviour
    // and exactly what is wanted - a handcuffs arrow must never merge into a
    // rope arrow. Arrows of Electrization carry no NBT and so always stack.
    //
    // NEITHER is in the minecraft:arrows item tag. That omission is
    // load-bearing, not an oversight: it is what stops every other bow and
    // crossbow in the game from loading them, which is [stated]'s "it should be
    // the only one able to fire restraining arrows (so other modded bows cant)"
    // and the same requirement repeated for the electrization arrow. See
    // ReinforcedBowItem#REINFORCED_AMMO and AbstractReinforcedArrow.
    //
    // RENAMED AT 1.6.1: these were registered as "restraining_arrow" in 1.6.0.
    // The id changed along with the display name so the two arrows read as one
    // family; any 1.6.0 arrow in an existing world is simply gone.
    public static final RegistryObject<Item> ARROW_OF_RESTRAINT = ITEMS.register("arrow_of_restraint",
            () -> new ArrowOfRestraintItem(new Item.Properties()));

    public static final RegistryObject<Item> ARROW_OF_ELECTRIZATION = ITEMS.register("arrow_of_electrization",
            () -> new ArrowOfElectrizationItem(new Item.Properties()));
}
