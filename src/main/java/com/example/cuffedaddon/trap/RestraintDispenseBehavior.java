package com.example.cuffedaddon.trap;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockSource;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.example.cuffedaddon.config.CuffedAddonServerConfig;
import com.lazrproductions.cuffed.api.CuffedAPI;
import com.lazrproductions.cuffed.cap.RestrainableCapability;
import com.lazrproductions.cuffed.restraints.RestraintAPI;
import com.lazrproductions.cuffed.restraints.base.AbstractArmRestraint;
import com.lazrproductions.cuffed.restraints.base.AbstractHeadRestraint;
import com.lazrproductions.cuffed.restraints.base.AbstractLegRestraint;
import com.lazrproductions.cuffed.restraints.base.AbstractRestraint;
import com.lazrproductions.cuffed.restraints.base.RestraintType;

/**
 * Dispenser trap for the three ordinary restraint slots.
 *
 * <p>Registered for EVERY restraint item Cuffed knows about, ours included - see
 * {@link RestraintTraps}. That deliberately REPLACES Cuffed's own four
 * registrations (handcuffs, fuzzy handcuffs, shackles, bundle) so that every
 * restraint in the game behaves the same way in a dispenser, instead of four
 * items following Cuffed's item-class rule and the other dozen doing nothing at
 * all.
 *
 * <h2>Failure is a normal dispense, not a no-op</h2>
 * Every rejection path ends in {@code super.execute(...)}, which is vanilla's
 * ordinary "throw the item on the floor" behaviour - [stated]'s "it should be
 * spitted out instead like a dispensed item normally would". Nothing here ever
 * swallows an item. The dispense sound and the smoke particle are played by
 * {@code DefaultDispenseItemBehavior#dispense} either way, so a trap that fires
 * and a trap that spits look and sound alike from outside.
 *
 * <h2>Captor</h2>
 * The restrained player is passed as their own captor, which is exactly what
 * Cuffed's own dispenser path does
 * ({@code getRestraintFromStack(stack, type, player, player)}). A dispenser has
 * no player behind it, and {@code AbstractRestraint}'s constructor and
 * {@code onEquippedServer} both dereference the captor without a null check, so
 * SOMETHING has to be passed; matching Cuffed means this travels the exact code
 * path their four items have already travelled in production rather than
 * inventing a fake-player captor whose side effects nobody has tested.
 */
public class RestraintDispenseBehavior extends DefaultDispenseItemBehavior {

    @Override
    protected ItemStack execute(BlockSource source, ItemStack stack) {
        if (!CuffedAddonServerConfig.TRAP_DISPENSER_RESTRAIN_ENABLED.get()) {
            return super.execute(source, stack);
        }
        return tryRestrain(source, stack) ? shrink(stack) : super.execute(source, stack);
    }

    private static ItemStack shrink(ItemStack stack) {
        stack.shrink(1);
        return stack;
    }

    private static boolean tryRestrain(BlockSource source, ItemStack stack) {
        ServerLevel level = source.getLevel();
        BlockPos targetPos = TrapDispenseUtil.targetPos(source);
        ServerPlayer target = TrapDispenseUtil.playerAt(level, targetPos);
        if (target == null) {
            return false;
        }

        Direction facing = TrapDispenseUtil.facing(source);
        RestraintType slot = TrapDispenseUtil.slotFor(facing, targetPos, target);
        if (slot == null) {
            return false;
        }

        // Does this item even exist as a restraint for THIS slot? This is the
        // id-based registry lookup rather than getRestraintFromStack, and it is
        // the whole "fuzzy handcuffs on legs spits out" rule in one line: an
        // arms-only item returns null for Leg and Head.
        if (RestraintAPI.Registries.get(stack.getItem(), slot) == null) {
            return false;
        }

        RestrainableCapability cap =
                (RestrainableCapability) CuffedAPI.Capabilities.getRestrainableCapability(target);
        if (cap == null || cap.isRestrained(slot)) {
            return false;
        }

        // One item's worth, so the restraint remembers the right enchantments and
        // damage without carrying the rest of the dispenser's stack with it.
        ItemStack single = stack.copyWithCount(1);
        if (!RestraintAPI.canEquipRestriantItem(single, slot, target, target)) {
            // Cuffed's own pre-equip veto. Spit rather than silently eat the item.
            return false;
        }
        // A Bundle only counts as a head restraint while it is EMPTY. Checked
        // explicitly because BundleRestraint#canEquipRestraintItem does not
        // actually reject a full one - it returns true on both branches - so
        // Cuffed guards this in its own dispenser path instead, and so must we
        // or a full Bundle would go on someone's head and its contents would
        // vanish into the restraint. Same condition this addon already uses in
        // WallPoseEvents.
        if (single.is(Items.BUNDLE) && BundleItem.getFullnessDisplay(single) > 0) {
            return false;
        }

        AbstractRestraint restraint = RestraintAPI.getRestraintFromStack(single, slot, target, target);
        return equip(cap, target, slot, restraint);
    }

    private static boolean equip(RestrainableCapability cap, ServerPlayer target, RestraintType slot,
                                 @Nullable AbstractRestraint restraint) {
        if (restraint == null) {
            return false;
        }
        if (slot == RestraintType.Arm && restraint instanceof AbstractArmRestraint arm) {
            return cap.TryEquipRestraint(target, target, arm);
        }
        if (slot == RestraintType.Leg && restraint instanceof AbstractLegRestraint leg) {
            return cap.TryEquipRestraint(target, target, leg);
        }
        if (slot == RestraintType.Head && restraint instanceof AbstractHeadRestraint head) {
            return cap.TryEquipRestraint(target, target, head);
        }
        return false;
    }
}
