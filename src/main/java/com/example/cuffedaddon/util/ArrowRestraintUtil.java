package com.example.cuffedaddon.util;

import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import com.lazrproductions.cuffed.api.CuffedAPI;
import com.lazrproductions.cuffed.cap.RestrainableCapability;
import com.lazrproductions.cuffed.restraints.RestraintAPI;
import com.lazrproductions.cuffed.restraints.base.AbstractArmRestraint;
import com.lazrproductions.cuffed.restraints.base.AbstractHeadRestraint;
import com.lazrproductions.cuffed.restraints.base.AbstractLegRestraint;
import com.lazrproductions.cuffed.restraints.base.AbstractRestraint;
import com.lazrproductions.cuffed.restraints.base.RestraintType;

/**
 * Puts the restraint a Restraining Arrow was carrying onto the player it hit.
 *
 * <h2>The slot rule</h2>
 * [stated]'s spec, in their words: "Since there are 3 restraint slots, the
 * Restraint Arrow should always prioritize arms, then legs, and lastly head.
 * Arrows containing head only restraints (bundles etc) should always prioritize
 * the head."
 *
 * <p>Both halves of that are one loop over {@link #PRIORITY}, and the second
 * half needs no special case at all. Whether an item can fill a given slot is
 * answered by {@code RestraintAPI.Registries.get(item, slot)} - a registry
 * lookup, not an item-class test - and a head-only item like the Bundle or a
 * Sleep Mask simply returns null for Arm and for Leg. So a head-only arrow
 * "prioritises the head" because the first two rungs of the ladder do not exist
 * for it. An ambiguous item (Rope, Straitjacket) exists on all three rungs and
 * therefore lands on arms first. Handcuffs exist on two and land on arms, or on
 * legs if the arms are already taken.
 *
 * <p>That lookup is also what covers restraints neither mod has written yet:
 * anything registered into any restraint registry Cuffed knows about is handled
 * here with no list to keep in sync, exactly as {@code RestraintTraps} does it.
 *
 * <h2>When nothing is free</h2>
 * The arrow is simply wasted - [stated]: "If for example a head restraint arrow
 * lands on a player already head restrained, it should act like a normal arrow
 * does and just do damage, wasting the restraining effect." Nothing is dropped
 * and nothing is refunded. The damage has already happened by the time this
 * runs ({@code AbstractArrow#onHitEntity} calls {@code doPostHurtEffects} only
 * after a successful {@code hurt}), so "just do damage" is what a false return
 * from here means, with no extra code.
 *
 * <h2>Captor</h2>
 * The shooter, when the shooter is a player; otherwise the target themselves.
 * {@code AbstractRestraint}'s constructor and {@code onEquippedServer} both
 * dereference the captor with no null check, so something must be passed, and
 * passing the victim is precisely what Cuffed's own dispenser path does
 * ({@code getRestraintFromStack(stack, type, player, player)}) and what this
 * addon's dispenser traps already do. Note the shooter CAN be the target - a
 * player who shoots themselves is captor and victim at once, which is a case
 * [stated] asked for explicitly and which needs nothing special here.
 */
public final class ArrowRestraintUtil {

    /** Arms, then legs, then head. [stated]'s stated order; see class doc. */
    public static final RestraintType[] PRIORITY = {
            RestraintType.Arm, RestraintType.Leg, RestraintType.Head
    };

    /**
     * Restraints that get no arrow, by registry id.
     *
     * <p>Cuffed registers the PILLORY as a restraint like any other, so 1.6.0 -
     * which read the whole registry - handed it an arrow too. [stated] saw it in
     * the creative tab and asked for it gone: <i>"I asked for the 3 slot
     * restraints and not the stationary ones and this is incredibly buggy."</i>
     * A pillory is furniture that holds a player at a fixed block; nothing about
     * it survives being applied to whoever happens to be standing where an arrow
     * landed, which is why it misbehaved.
     *
     * <p>This set is the creative tab's half of that. The recipes' half is
     * simply that no {@code arrow_of_restraint} JSON names the pillory - the
     * recipe list became explicit at 1.6.1 for exactly this reason. <b>The two
     * must agree:</b> a restraint with an arrow in the tab and no recipe is an
     * item players cannot make, and one with a recipe and no tab entry is
     * invisible in creative.
     *
     * <p>Checked by ID rather than by Item, because the pillory's item is a
     * block item this addon has no reference to and resolving it at class-init
     * time would mean a registry lookup before the registries are frozen.
     */
    public static final Set<ResourceLocation> EXCLUDED = Set.of(
            ResourceLocation.fromNamespaceAndPath("cuffed", "pillory"));

    /** Whether this item is allowed to become an Arrow of Restraint. */
    public static boolean isArrowable(Item item) {
        if (item == null) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        return id != null && !EXCLUDED.contains(id);
    }

    private ArrowRestraintUtil() {
    }

    /**
     * @param target   the player the arrow hit
     * @param restraint the restraint item stack the arrow was carrying
     * @param shooter  whoever fired the arrow, if that was a player
     * @return true if a restraint was actually equipped
     */
    public static boolean apply(ServerPlayer target, ItemStack restraint, @Nullable ServerPlayer shooter) {
        if (restraint.isEmpty() || !isArrowable(restraint.getItem())) {
            // The exclusion is enforced here as well as at craft time, so an
            // arrow made by an older build cannot still apply a pillory.
            return false;
        }
        RestrainableCapability cap =
                (RestrainableCapability) CuffedAPI.Capabilities.getRestrainableCapability(target);
        if (cap == null) {
            return false;
        }
        ServerPlayer captor = shooter != null ? shooter : target;

        for (RestraintType slot : PRIORITY) {
            // Can this item be this kind of restraint at all?
            if (RestraintAPI.Registries.get(restraint.getItem(), slot) == null) {
                continue;
            }
            if (cap.isRestrained(slot)) {
                continue;
            }
            // One item's worth, carrying the payload's own enchantments and
            // damage value and nothing else.
            ItemStack single = restraint.copyWithCount(1);
            // A Bundle is only a head restraint while it is EMPTY. Checked here
            // for the same reason the dispenser traps check it: Cuffed's own
            // BundleRestraint#canEquipRestraintItem returns true on both
            // branches, so without this a full Bundle would go on a head and its
            // contents would vanish into the restraint. The crafting recipe
            // refuses a full Bundle too, so this is the second of two gates.
            if (single.is(Items.BUNDLE) && BundleItem.getFullnessDisplay(single) > 0) {
                continue;
            }
            // Cuffed's own pre-equip veto, consulted per slot rather than once -
            // a restraint that refuses the arms may still accept the legs.
            if (!RestraintAPI.canEquipRestriantItem(single, slot, target, captor)) {
                continue;
            }
            AbstractRestraint built = RestraintAPI.getRestraintFromStack(single, slot, target, captor);
            if (equip(cap, target, captor, slot, built)) {
                return true;
            }
        }
        return false;
    }

    private static boolean equip(RestrainableCapability cap, ServerPlayer target, ServerPlayer captor,
                                 RestraintType slot, @Nullable AbstractRestraint restraint) {
        if (restraint == null) {
            return false;
        }
        if (slot == RestraintType.Arm && restraint instanceof AbstractArmRestraint arm) {
            return cap.TryEquipRestraint(target, captor, arm);
        }
        if (slot == RestraintType.Leg && restraint instanceof AbstractLegRestraint leg) {
            return cap.TryEquipRestraint(target, captor, leg);
        }
        if (slot == RestraintType.Head && restraint instanceof AbstractHeadRestraint head) {
            return cap.TryEquipRestraint(target, captor, head);
        }
        return false;
    }
}
