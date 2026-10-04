package com.example.cuffedaddon.init;

import java.util.LinkedHashSet;
import java.util.Set;

import com.example.cuffedaddon.CuffedAddon;
import com.example.cuffedaddon.items.ArrowOfRestraintItem;
import com.example.cuffedaddon.util.ArrowRestraintUtil;
import com.lazrproductions.cuffed.init.ModCreativeTabs;
import com.lazrproductions.cuffed.restraints.RestraintAPI;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegistryObject;

/**
 * Cuffed builds its creative tab contents inline inside its own DeferredRegister
 * lambda, so there's nothing to "add to" at registration time. Instead we hook
 * BuildCreativeModeTabContentsEvent (fired for every tab, from every mod, on the
 * mod event bus) and append our item whenever it's Cuffed's tab being built.
 *
 * <h2>Why every lookup here is guarded (1.5.16)</h2>
 * This listener does not only run at startup. {@code CreativeModeInventoryScreen}
 * calls {@code CreativeModeTabs.tryRebuildTabContents} every time the screen is
 * opened, and rebuilds whenever the feature flags, the operator-items setting or
 * the {@code registryAccess} differ from the cached ones - which they always do
 * the first time you open your inventory <b>after joining a server</b>, because
 * that registry access comes from the server. So the whole event is re-posted to
 * every mod, in-world, on the client.
 *
 * <p>That matters because of what Forge does on connect. {@code GameData
 * .injectSnapshot} rebuilds the client's registries from the SERVER's snapshot,
 * and any entry the client has that the server's snapshot does not is simply
 * dropped; {@code RegistryObject.updateReference} then sets that object's value
 * to <b>null</b>. A later {@code .get()} throws
 * "Registry Object not present: &lt;id&gt;", and because this is a mod-bus event
 * Forge wraps that in a {@code ModLoadingException} and <b>crashes the client the
 * moment the player presses E</b>.
 *
 * <p>[stated] hit exactly this on their own server at 1.5.15 - from a different
 * mod, whose tab listener calls {@code .get()} unguarded the way this one used
 * to. The trigger there was a client/server mod mismatch, and this addon is
 * unusually easy to mismatch: its jar filename does not carry the version, so an
 * old copy on the server and a new copy on the client look identical in a folder
 * listing. Under that mismatch every item this addon added in the newer build -
 * at 1.5.15, the two enchantment books - would be dropped on the client and would
 * have crashed here in the same way.
 *
 * <p>So nothing here dereferences a RegistryObject without checking it first, and
 * a miss is logged once and skipped. The creative tab quietly missing an item is
 * a much better failure than a client that cannot open its inventory, and the log
 * line names what went missing, which is the actual diagnosis.
 */
public class ModCreativeTabContent {

    /** One log line per session, not one per tab rebuild. */
    private static boolean reportedMissing = false;

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModCreativeTabContent::addToCuffedTab);
        modEventBus.addListener(ModCreativeTabContent::addToCombatTab);
    }

    private static void addToCuffedTab(BuildCreativeModeTabContentsEvent event) {
        // Cuffed's own tab object can be unbound by the same mechanism, so this
        // is checked before it is dereferenced too.
        if (!ModCreativeTabs.CUFFED_TAB.isPresent() || event.getTab() != ModCreativeTabs.CUFFED_TAB.get()) {
            return;
        }

        // [stated]'s explicit requested order (1.4.19): Reinforced Bed
        // colors first, then Rope, Bed Restraint, Wall Restraint, Sleep
        // Mask, the 4 combo restraints, then Block Locker. Player Picker
        // wasn't mentioned in that ordering request, so it stays last.
        for (String color : ModBlocks.BED_COLORS) {
            accept(event, ModItems.REINFORCED_BED_ITEMS.get(color));
        }
        accept(event, ModItems.ROPE);
        // Straitjacket wasn't covered by [stated]'s explicit 1.4.19
        // ordering request (it didn't exist yet) - placed right after
        // Rope since it's the same kind of ambiguous arms/legs/head
        // item; revisit if a different spot is wanted.
        accept(event, ModItems.STRAITJACKET);
        accept(event, ModItems.BED_RESTRAINT);
        accept(event, ModItems.WALL_RESTRAINT);
        accept(event, ModItems.SLEEP_MASK);
        accept(event, ModItems.BUNDLE_DUCT_TAPE);
        accept(event, ModItems.BUNDLE_ROPE);
        accept(event, ModItems.SLEEP_MASK_DUCT_TAPE);
        accept(event, ModItems.SLEEP_MASK_ROPE);
        accept(event, ModItems.BLOCK_LOCKER);
        accept(event, ModItems.PLAYER_PICKER);
        // Shock Collar line (1.4.32) - also not covered by the 1.4.19
        // ordering request, so appended after Player Picker in crafting
        // order (components first, finished item last). Say if a different
        // spot is wanted.
        accept(event, ModItems.UNBOUND_COLLAR);
        accept(event, ModItems.SHOCK_REMOTE);
        accept(event, ModItems.SHOCK_COLLAR);
        // Restraint enchantments (1.5.15), as enchanted books at every level,
        // last in the tab. Books rather than nothing at all because an
        // enchantment is not an item: the only way to hand one to a creative
        // player is the book, and for Restraint Gaze this is the ONLY way it
        // can be obtained at all (see RestraintGazeEnchantment). Cuffed's own
        // tab lists no books for its six, so these do not duplicate anything.
        acceptBooks(event, ModEnchantments.REPELLENT);
        acceptBooks(event, ModEnchantments.RESTRAINT_GAZE);
        acceptBooks(event, ModEnchantments.ILLUSION);
        // Reinforced Bow (1.6.0), appended last for the same reason the Shock
        // Collar line was - no ordering request covers it. Its ARROWS are not
        // here: [stated] asked at 1.6.1 for those to sit "at the end of the
        // creative's Combat tab, with all the other tipped arrows", so they are
        // added by addToCombatTab below. The bow was not mentioned and stays
        // put; say if it should follow them.
        accept(event, ModItems.REINFORCED_BOW);
    }

    /**
     * The two special arrows, appended to the end of vanilla's Combat tab.
     *
     * <p>[stated]'s placement request, and the natural one: vanilla's own
     * tipped arrows are the last thing in that tab, so these land immediately
     * after them among the arrows a player already thinks of as "special
     * arrows" rather than buried among restraint items.
     *
     * <p>Arrows of Restraint are listed <b>loaded</b>, one per restraint, for
     * the same reason the enchantments are listed as books: the restraint lives
     * in NBT and crafting is otherwise the only way to put it there, so an
     * empty arrow would be a useless entry. Each one is built by
     * {@code ArrowOfRestraintItem.withRestraint}, the same call the recipes and
     * the dropped-arrow pickup use - that is what makes a creative-grabbed
     * arrow byte-identical to a crafted one, which is in turn what lets JEI's
     * recipe key find a recipe for it.
     *
     * <p>The list comes from Cuffed's live registries minus
     * {@code ArrowRestraintUtil.EXCLUDED} (which is how the pillory stays out),
     * de-duplicated because a single item can be registered for more than one
     * slot - handcuffs are both an arm and a leg restraint and would otherwise
     * appear twice.
     *
     * <p>Everything here is guarded for the reason the Cuffed-tab listener is:
     * this is a mod-bus listener that re-runs in-world, and an exception from it
     * crashes the client on inventory open. A bad entry is skipped silently.
     */
    private static void addToCombatTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != CreativeModeTabs.COMBAT) {
            return;
        }
        if (!ModItems.ARROW_OF_RESTRAINT.isPresent()) {
            reportMissing(ModItems.ARROW_OF_RESTRAINT);
        } else {
            Set<Item> restraintItems = new LinkedHashSet<>(RestraintAPI.Registries.getAllRestraintItems());
            for (Item item : restraintItems) {
                if (ArrowRestraintUtil.isArrowable(item)) {
                    event.accept(ArrowOfRestraintItem.withRestraint(new ItemStack(item)));
                }
            }
        }
        accept(event, ModItems.ARROW_OF_ELECTRIZATION);
    }

    private static void accept(BuildCreativeModeTabContentsEvent event, RegistryObject<? extends ItemLike> item) {
        if (item == null || !item.isPresent()) {
            reportMissing(item);
            return;
        }
        event.accept(item.get());
    }

    private static void acceptBooks(BuildCreativeModeTabContentsEvent event,
                                    RegistryObject<Enchantment> enchantment) {
        if (enchantment == null || !enchantment.isPresent()) {
            reportMissing(enchantment);
            return;
        }
        Enchantment value = enchantment.get();
        for (int level = value.getMinLevel(); level <= value.getMaxLevel(); level++) {
            event.accept(EnchantedBookItem.createForEnchantment(new EnchantmentInstance(value, level)));
        }
    }

    private static void reportMissing(RegistryObject<?> object) {
        if (reportedMissing) {
            return;
        }
        reportedMissing = true;
        CuffedAddon.LOGGER.warn("Leaving {} out of the creative tab: it is registered here but not bound. "
                        + "On a client this almost always means the server is running a different build of "
                        + "this addon, so its registry snapshot did not contain that entry. Check that the "
                        + "server and client jars match - this addon's filename does not carry its version, "
                        + "so a stale copy on one side is easy to miss.",
                object == null ? "an unknown entry" : object.getId());
    }
}
