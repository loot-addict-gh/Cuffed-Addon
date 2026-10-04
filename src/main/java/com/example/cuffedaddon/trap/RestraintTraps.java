package com.example.cuffedaddon.trap;

import java.util.LinkedHashSet;
import java.util.Set;

import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.registries.ForgeRegistries;

import com.example.cuffedaddon.CuffedAddon;
import com.example.cuffedaddon.init.ModItems;
import com.lazrproductions.cuffed.items.base.AbstractRestraintKeyItem;
import com.lazrproductions.cuffed.restraints.RestraintAPI;

/**
 * Wires every dispenser trap behaviour onto its items.
 *
 * <h2>Why this runs at FMLLoadCompleteEvent and not in common setup</h2>
 * Two independent reasons, either of which on its own would be enough:
 *
 * <ol>
 *   <li><b>Thread safety.</b> {@code DispenserBlock}'s behaviour registry is a
 *       plain unsynchronised map, and <b>Cuffed writes to it directly from its
 *       own {@code FMLCommonSetupEvent} handler with no {@code enqueueWork}</b>
 *       (see {@code CuffedMod#commonSetup}), so that write happens on a
 *       ForkJoinPool worker. This is the exact shape of the
 *       {@code ItemProperties.register} race that crashed this addon during
 *       sided_setup once already - two threads in {@code computeIfAbsent} on one
 *       map, an intermittent ConcurrentModificationException, and a crash report
 *       that blames the wrong mod. Wrapping our call in {@code enqueueWork}
 *       would NOT fix it, because the main thread drains that queue while the
 *       parallel handlers are still running. {@code FMLLoadCompleteEvent} is
 *       dispatched only after every mod's setup has finished, so there is no
 *       one left to race.</li>
 *   <li><b>Ordering.</b> Cuffed registers its own behaviour for handcuffs, fuzzy
 *       handcuffs, shackles and the Bundle. {@code registerBehavior} overwrites,
 *       and we deliberately want ours to win for those four so that every
 *       restraint in the game follows one consistent aiming rule (see
 *       {@link TrapDispenseUtil}). Registering strictly after all setup
 *       guarantees that, where two parallel setup handlers would be a coin
 *       flip.</li>
 * </ol>
 *
 * <h2>Which items get which behaviour</h2>
 * The restraint list is taken from {@code RestraintAPI.Registries} rather than
 * hardcoded, so this covers Cuffed's restraints, this addon's, and any future
 * one either mod adds, without a list to keep in sync. Keys are found the same
 * way Cuffed identifies them everywhere else - {@code AbstractRestraintKeyItem} -
 * so the handcuffs key and the shackles key both work, each opening only what it
 * is allowed to open.
 */
public final class RestraintTraps {

    private static final DispenseItemBehavior RESTRAINT = new RestraintDispenseBehavior();
    private static final DispenseItemBehavior KEY = new RestraintKeyDispenseBehavior();
    private static final DispenseItemBehavior BED = new BedRestraintDispenseBehavior();

    private RestraintTraps() {
    }

    public static void registerDispenserBehaviours(FMLLoadCompleteEvent event) {
        event.enqueueWork(RestraintTraps::register);
    }

    private static void register() {
        int restraints = 0;
        // A single item can be registered for more than one slot (handcuffs are
        // both an arm and a leg restraint), so de-duplicate before counting -
        // registering twice would be harmless but the log line would lie.
        Set<Item> restraintItems = new LinkedHashSet<>(RestraintAPI.Registries.getAllRestraintItems());
        for (Item item : restraintItems) {
            if (item != null) {
                DispenserBlock.registerBehavior(item, RESTRAINT);
                restraints++;
            }
        }

        int keys = 0;
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            if (item instanceof AbstractRestraintKeyItem) {
                DispenserBlock.registerBehavior(item, KEY);
                keys++;
            }
        }

        DispenserBlock.registerBehavior(ModItems.BED_RESTRAINT.get(), BED);

        CuffedAddon.LOGGER.info(
                "Restraint traps: registered dispenser behaviours for {} restraint items, {} keys, and the Bed Restraint.",
                restraints, keys);
    }
}
