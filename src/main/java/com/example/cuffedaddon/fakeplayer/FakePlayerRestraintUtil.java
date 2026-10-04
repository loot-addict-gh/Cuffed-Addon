package com.example.cuffedaddon.fakeplayer;

import com.example.cuffedaddon.enchantment.ConjuredRestraints;
import com.example.cuffedaddon.CuffedAddon;
import com.example.cuffedaddon.capability.ModCapabilities;
import com.example.cuffedaddon.network.FakeRestraintSyncPacket;
import com.example.cuffedaddon.network.NetworkHandler;
import com.lazrproductions.cuffed.items.base.AbstractRestraintKeyItem;
import com.lazrproductions.cuffed.restraints.RestraintAPI;
import com.lazrproductions.cuffed.restraints.base.AbstractRestraint;
import com.lazrproductions.cuffed.restraints.base.RestraintType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;

/**
 * Every restraint state transition for a fake player, so the interact listener,
 * the mixin and the renderer can't drift on what "equipped" means. The fake-player
 * counterpart to {@code ShockCollarUtil}.
 *
 * <h2>Matching real-player behaviour exactly</h2>
 * [stated] asked for restraints to be "applicable and removable by their
 * respective keys just as if it was a real player", so the slot chosen for an
 * interaction uses Cuffed's OWN rule, lifted from
 * {@code RestrainableCapability#onInteractedByOther}: the local hit height on the
 * target picks the slot.
 * <pre>
 *   above 1.5   -> Head
 *   0.33 to 1.5 -> Arm
 *   at or below 0.33 -> Leg
 * </pre>
 * That is what makes an "ambiguous" item like Rope or the Straitjacket - one item
 * that fits all three slots - land where you aimed, identically to a real player.
 * Keys use the same bands, and the same "this key opens this restraint" test
 * Cuffed uses: a restraint with a null key item can be removed by any key.
 *
 * <h2>What is deliberately NOT here</h2>
 * No durability, no struggling, no escorting, no restraint tick. A fake player has
 * no {@code AbstractRestraint} instance at all - see {@link IFakeRestrained} for
 * why that is both necessary and sufficient. Restraints on a fake player come off
 * exactly one way: with the right key.
 */
public final class FakePlayerRestraintUtil {

    /** Cuffed's own interaction-height bands. Do not diverge from these. */
    private static final double HEAD_ABOVE = 1.5d;
    private static final double ARM_ABOVE = 0.33d;

    private FakePlayerRestraintUtil() {
    }

    @Nullable
    public static IFakeRestrained get(Entity entity) {
        return entity.getCapability(ModCapabilities.FAKE_RESTRAINED).orElse(null);
    }

    /** The slot an interaction at this local height addresses. */
    public static RestraintType slotForHeight(double interactionHeight) {
        if (interactionHeight > HEAD_ABOVE) {
            return RestraintType.Head;
        }
        if (interactionHeight > ARM_ABOVE) {
            return RestraintType.Arm;
        }
        return RestraintType.Leg;
    }

    // ---------------------------------------------------------------- apply

    /**
     * Puts the restraint carried by {@code stack} onto {@code target}'s slot.
     *
     * <p>Uses {@link RestraintAPI.Registries#get(net.minecraft.world.item.Item,
     * RestraintType)} - the id-based lookup - rather than
     * {@code getRestraintFromStack}, which takes two {@code ServerPlayer}s and so
     * cannot be called for a mob. That lookup also naturally answers "does this
     * item even fit this slot": a head-only item returns null for Arm.
     *
     * @return true if something was equipped.
     */
    public static boolean tryApply(ServerPlayer actor, LivingEntity target, ItemStack stack,
                                    RestraintType slot) {
        IFakeRestrained cap = get(target);
        if (cap == null) {
            reportFirstAttempt("no restraint capability on the entity", stack, slot);
            return false;
        }
        if (cap.has(slot)) {
            reportFirstAttempt("that slot is already occupied", stack, slot);
            return false;
        }
        AbstractRestraint restraint = RestraintAPI.Registries.get(stack.getItem(), slot);
        if (restraint == null) {
            reportFirstAttempt("Cuffed has no restraint registered for that item in that slot", stack, slot);
            return false;
        }
        reportFirstAttempt(null, stack, slot);

        cap.setRestraintId(slot, restraint.getId());
        cap.setEnchanted(slot, stack.isEnchanted());
        // Keep the item itself, not just the fact that it was enchanted, so that
        // unlocking hands back the same handcuffs that went on - same durability,
        // same enchantments, same name. See IFakeRestrained#getStack.
        cap.setStack(slot, stack.copyWithCount(1));

        if (!actor.isCreative()) {
            stack.shrink(1);
        }
        playAt(target, restraint.getEquipSound());
        onRestraintsChanged(target, cap);
        return true;
    }

    // --------------------------------------------------------------- remove

    /**
     * Takes the restraint off {@code slot} if {@code stack} is a key that opens it.
     *
     * <p>The key test mirrors Cuffed's: a restraint whose {@code getKeyItem()} is
     * null is opened by any key, otherwise the key item has to match exactly.
     *
     * @return true if something was removed.
     */
    public static boolean tryRemove(ServerPlayer actor, LivingEntity target, ItemStack stack,
                                     RestraintType slot) {
        IFakeRestrained cap = get(target);
        if (cap == null || !cap.has(slot)) {
            return false;
        }
        boolean emptyHand = stack.isEmpty();
        if (!emptyHand && !(stack.getItem() instanceof AbstractRestraintKeyItem)) {
            return false;
        }

        ResourceLocation id = cap.getRestraintId(slot);
        AbstractRestraint restraint = id == null ? null : RestraintAPI.Registries.get(id);
        if (restraint == null) {
            // The id no longer resolves - Cuffed changed, or the restraint came
            // from a mod that has since been removed. Let any key clear it rather
            // than leaving the slot permanently stuck. The stored stack is still
            // handed back if there is one: the registry entry is gone, but the
            // item the player put on may well still exist.
            ItemStack orphaned = takeStoredStack(cap, slot, null);
            clearSlot(target, cap, slot, null);
            giveBack(actor, orphaned);
            return true;
        }
        // Cuffed's own two rules, from RestrainableCapability#onInteractedByOther:
        //   with a KEY       - a restraint opens if its key item is null (any key
        //                      works) or is exactly the key being held;
        //   with an EMPTY HAND - only a restraint whose key item is null opens.
        // The second is how Rope and the Straitjacket come off a real player at
        // all - they have no key item, which is what their tooltips mean by
        // "my key: empty hand". Missing it is why everything except handcuffs,
        // fuzzy cuffs and shackles stayed on a fake player permanently in 1.5.2:
        // those three are the ones with a real key item.
        if (emptyHand) {
            if (restraint.getKeyItem() != null) {
                return false;
            }
        } else if (restraint.getKeyItem() != null && restraint.getKeyItem() != stack.getItem()) {
            return false;
        }

        ItemStack returned = takeStoredStack(cap, slot, restraint);
        clearSlot(target, cap, slot, restraint);
        giveBack(actor, returned);
        return true;
    }

    /**
     * What should come back off this slot: the stack that was put on if it was
     * recorded, otherwise a fresh item built from the restraint.
     *
     * <p>The fallback covers two cases that are not going away - a world saved
     * before the stack was stored, and a restraint applied by some route that
     * never had a stack to store.
     */
    private static ItemStack takeStoredStack(IFakeRestrained cap, RestraintType slot,
                                              @Nullable AbstractRestraint restraint) {
        ItemStack stored = cap.getStack(slot);
        if (!stored.isEmpty()) {
            return stored.copy();
        }
        if (restraint == null || restraint.getItem() == null) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(restraint.getItem());
    }

    /**
     * Drops everything a fake player is wearing on the ground where it died, and
     * empties the capability.
     *
     * <h2>Why this exists</h2>
     * Without it the restraints are simply destroyed with the entity. Cuffed does
     * not do that to a real player - {@code RestrainableCapability#onDeathServer}
     * unequips each slot with a null releaser, which is the branch of
     * {@code UnequipRestraint} that spawns an {@code ItemEntity} at the body. A
     * fake player should not be a way to lose a pair of handcuffs.
     *
     * <h2>The one place this deliberately differs from Cuffed</h2>
     * Cuffed keeps a restraint enchanted with Curse of Binding ON through death,
     * so it is still there when the player respawns. A fake player does not
     * respawn, so "keep it equipped" would mean "delete it". Everything is
     * dropped, curse or not. This capability stores only whether a slot is
     * enchanted, not with what, so the distinction could not be drawn here
     * anyway - and destroying the item is the worse of the two answers.
     */
    public static void dropAllOnDeath(LivingEntity target) {
        IFakeRestrained cap = get(target);
        if (cap == null || !cap.isRestrained()) {
            return;
        }
        for (RestraintType slot : RestraintType.values()) {
            ResourceLocation id = cap.getRestraintId(slot);
            if (id == null) {
                continue;
            }
            AbstractRestraint restraint = RestraintAPI.Registries.get(id);
            ItemStack dropStack = takeStoredStack(cap, slot, restraint);
            cap.setRestraintId(slot, null);
            cap.setEnchanted(slot, false);
            cap.setStack(slot, ItemStack.EMPTY);
            if (!dropStack.isEmpty()) {
                ItemEntity dropped = new ItemEntity(target.level(), target.getX(),
                        target.getY() + 0.6d, target.getZ(), dropStack);
                dropped.setDefaultPickUpDelay();
                target.level().addFreshEntity(dropped);
            }
        }
        onRestraintsChanged(target, cap);
    }

    private static void clearSlot(LivingEntity target, IFakeRestrained cap, RestraintType slot,
                                   @Nullable AbstractRestraint restraint) {
        cap.setRestraintId(slot, null);
        cap.setEnchanted(slot, false);
        cap.setStack(slot, ItemStack.EMPTY);
        if (restraint != null) {
            playAt(target, restraint.getUnequipSound());
        }
        onRestraintsChanged(target, cap);
    }

    /**
     * Hands the restraint item back to whoever unlocked it, dropping it at the
     * fake player if there is no room - the same courtesy
     * {@code /cuffed <player> remove Collar} extends.
     *
     * <p>Unless the restraint was conjured by Restraint Gaze (1.5.15), in which
     * case there is no item to hand back - it vanishes. This is one of the two
     * suppression points {@code ConjuredRestraints} describes, and it is here
     * rather than in the two callers because both of them, and
     * {@code dropAllOnDeath} aside, end up in this one method.
     */
    private static void giveBack(ServerPlayer actor, ItemStack returned) {
        if (returned.isEmpty() || ConjuredRestraints.isConjured(returned)) {
            return;
        }
        if (!actor.getInventory().add(returned)) {
            actor.drop(returned, false);
        }
    }

    // ------------------------------------------------------------- plumbing

    /**
     * Called after any change. Pushes the new state to everyone who can see this
     * entity, and stops any job the new state forbids from continuing to look
     * "running" while suppressed.
     */
    public static void onRestraintsChanged(LivingEntity target, IFakeRestrained cap) {
        sync(target, cap);
    }

    /**
     * Capabilities are not synced by Forge, and this state decides how the entity
     * RENDERS, so it has to reach every client tracking it - the same
     * broadcast-to-trackers pattern the Shock Collar uses, and for the same
     * reason. {@code PlayerEvent.StartTracking} covers anyone who walks into
     * range later; without it a fake player restrained before you arrived would
     * simply look unrestrained.
     */
    public static void sync(LivingEntity target, @Nullable IFakeRestrained cap) {
        if (target.level().isClientSide()) {
            return;
        }
        IFakeRestrained state = cap == null ? get(target) : cap;
        if (state == null) {
            return;
        }
        NetworkHandler.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target),
                new FakeRestraintSyncPacket(target.getId(), state.serializeNBT()));
    }

    private static boolean attemptReported;

    /**
     * Logs the outcome of the FIRST apply attempt only, then goes quiet.
     *
     * <p>Exists because "I right-click and nothing happens" has had, across 1.5.0
     * and 1.5.1, at least four indistinguishable causes spread between the
     * interaction, the capability, the mixins and the renderer. This says which
     * half of the problem is which in one line: if it reports success, the state
     * side works and anything still invisible is rendering. One attempt is enough
     * to answer that, and keeping it to one keeps it out of the way afterwards.
     *
     * @param failure why it did not apply, or null if it did.
     */
    private static void reportFirstAttempt(@Nullable String failure, ItemStack stack, RestraintType slot) {
        if (attemptReported) {
            return;
        }
        attemptReported = true;
        if (failure == null) {
            CuffedAddon.LOGGER.info("Fake Players compat: applied {} to the {} slot of a fake player.",
                    stack.getItem(), slot);
        } else {
            CuffedAddon.LOGGER.warn("Fake Players compat: could not apply {} to the {} slot of a fake player - {}.",
                    stack.getItem(), slot, failure);
        }
    }

    private static void playAt(LivingEntity target, @Nullable SoundEvent sound) {
        if (sound == null) {
            return;
        }
        target.level().playSound(null, target.blockPosition(), sound, SoundSource.PLAYERS, 1.0f, 1.0f);
    }
}
