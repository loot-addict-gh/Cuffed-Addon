package com.example.cuffedaddon.enchantment;

import com.example.cuffedaddon.CuffedAddon;
import com.example.cuffedaddon.mixin.AbstractRestraintItemDataAccessor;
import com.lazrproductions.cuffed.restraints.base.AbstractRestraint;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * A restraint that Restraint Gaze conjured out of nothing, and the machinery
 * that stops it becoming a real item.
 *
 * <h2>The requirement</h2>
 * [stated]: the gazed-on restraints "should have the same requirements as normal
 * to be removed (e.g. handcuffs and straitjackets require keys, rope and duct
 * tape doesnt) but should vanish when removed instead of dropping a duplicate".
 *
 * <p>So removal must stay completely untouched - Cuffed's key checks, the
 * lockpick, breaking out, {@code /cuffed <player> remove}, the release keybinds,
 * death, all of it - and only the very last step, handing an item back, is
 * suppressed. Anything that tried to special-case "is this restraint real" at
 * the removal decision would have had to be wired into every one of those paths
 * and would have changed how they behave.
 *
 * <h2>How it is marked</h2>
 * One boolean on the ItemStack's own NBT, set before the stack is ever turned
 * into a restraint. That is enough because
 * {@code AbstractRestraint}'s constructor saves the whole stack into
 * {@code itemData}, {@code serializeNBT} persists it and
 * {@code saveToItemStack} restores it - so the mark rides along through the
 * world save and comes back out on exactly the stack that would have been
 * dropped. Nothing has to be remembered anywhere else, and a restraint conjured
 * before a server restart still vanishes correctly afterwards.
 *
 * <p>Because Gaze copies are viral by [stated]'s choice - a conjured restraint
 * carries Restraint Gaze itself and can gaze at someone else - a copy of a copy
 * is marked too, since it is built from the wearer's own item data.
 *
 * <h2>The two suppression points, and why only two</h2>
 * Every route a restraint item can take back into the world is one of these:
 *
 * <ol>
 *   <li><b>A dropped {@code ItemEntity}.</b> Caught here, globally, by
 *       cancelling {@link EntityJoinLevelEvent} - which covers
 *       {@code UnequipRestraint} with no releaser, every
 *       {@code IBreakableRestraint#onBrokenServer} on both sides of the house,
 *       Cuffed's death handling, this addon's fake-player death drop, and any
 *       future path nobody has thought of yet. This is the same technique
 *       {@code DeathRestraintHandler} already uses for {@code freeAfterDeath},
 *       so the pattern is not new here.</li>
 *   <li><b>Straight into the releaser's inventory.</b> Only two places do this:
 *       {@code RestrainableCapability#UnequipRestraint} (handled by
 *       {@code RestrainableCapabilityConjuredMixin}) and this addon's own
 *       {@code FakePlayerRestraintUtil#giveBack} (handled inline there). Both
 *       check {@link #isConjured(ItemStack)} and skip the hand-back.</li>
 * </ol>
 *
 * <p>Deliberately NOT done: sweeping player inventories for marked stacks. It
 * would be a broader net, but it would also mean a conjured item could exist and
 * be seen for a tick or two before being deleted, and it would silently eat any
 * marked item an admin had deliberately created. The two points above are
 * exhaustive, so the invariant holds without one.
 */
@Mod.EventBusSubscriber(modid = CuffedAddon.MODID)
public final class ConjuredRestraints {

    /**
     * Namespaced to avoid colliding with anything Cuffed, Curios or a resource
     * pack might put on a restraint's NBT.
     */
    private static final String TAG = "CuffedAddonConjured";

    private ConjuredRestraints() {
    }

    /** Marks a stack as conjured, in place. */
    public static void mark(ItemStack stack) {
        if (!stack.isEmpty()) {
            stack.getOrCreateTag().putBoolean(TAG, true);
        }
    }

    public static boolean isConjured(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(TAG);
    }

    /**
     * Whether a worn restraint was conjured, read from its saved item data
     * without building an ItemStack - see
     * {@link AbstractRestraintItemDataAccessor} for why that matters.
     */
    public static boolean isConjured(@Nullable AbstractRestraint restraint) {
        if (!(restraint instanceof AbstractRestraintItemDataAccessor accessor)) {
            return false;
        }
        CompoundTag itemData = accessor.cuffedaddon$getItemData();
        return itemData != null && itemData.getCompound("tag").getBoolean(TAG);
    }

    /**
     * Suppression point 1: a conjured restraint never becomes a dropped item.
     *
     * <p>Server side only. The client is never told about an entity the server
     * refused to spawn, so there is nothing to cancel there, and cancelling on a
     * client would only risk desyncing an entity the server did keep.
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof ItemEntity item && isConjured(item.getItem())) {
            event.setCanceled(true);
        }
    }
}
