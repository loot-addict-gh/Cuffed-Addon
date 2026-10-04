package com.example.cuffedaddon;

import com.example.cuffedaddon.gamerule.ModGameRules;
import com.lazrproductions.cuffed.api.CuffedAPI;
import com.lazrproductions.cuffed.cap.base.IRestrainableCapability;
import com.lazrproductions.cuffed.restraints.RestraintAPI;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * All of the logic for keeping ordinary restraints on a player across death.
 *
 * This only deals with the restraint capability ({@link IRestrainableCapability})
 * - the head/arm/leg slots that handcuffs, shackles, bundles, duct tape etc.
 * occupy - gated behind the {@code freeAfterDeath} gamerule. It does not
 * touch, detect, or otherwise interact with Cuffed's separate pillory system
 * at all.
 *
 * 1. {@link #onDeathCapture} runs at HIGH priority on {@link LivingDeathEvent},
 *    i.e. before Cuffed's own (normal priority) handler runs. At this point
 *    the dying player is still wearing whatever restraints they had, so we
 *    snapshot the capability's NBT (exactly the same NBT Cuffed itself uses
 *    to sync/save restraints) and remember which items we expect Cuffed to
 *    drop a moment later.
 *
 * 2. Cuffed's own handler then runs at normal priority, unequips every
 *    restraint, and spawns a dropped ItemEntity for each one.
 *
 * 3. {@link #onItemJoin} (on {@link EntityJoinLevelEvent}) intercepts those
 *    dropped item entities the instant they're added to the world and
 *    cancels them - but only the exact items we recorded in step 1, so
 *    nothing else the player drops (e.g. a spare pair of cuffs sitting in
 *    their inventory) is ever touched.
 *
 * 4. {@link #onDeathCleanup} runs at LOW priority on the same LivingDeathEvent,
 *    after Cuffed's handler, and clears the "expected drops" bookkeeping for
 *    this player. This happens before vanilla's own inventory-drop-on-death
 *    logic runs (that logic isn't part of the LivingDeathEvent at all - it
 *    happens afterwards, inside LivingEntity#die()), so it can never
 *    accidentally suppress an unrelated item drop.
 *
 * 5. When the player respawns, {@link #onPlayerClone} re-applies the snapshot
 *    from step 1 onto the new player's capability.
 *
 * Everything here goes through Cuffed's public API only
 * (CuffedAPI / IRestrainableCapability / RestraintAPI) - no mixins of our
 * own, no reflection, and nothing is ever sent to chat.
 */
@Mod.EventBusSubscriber(modid = CuffedAddon.MODID)
public final class DeathRestraintHandler {

    /** Player UUID -> the capability NBT captured the instant before Cuffed unequips everything. */
    private static final Map<UUID, CompoundTag> PENDING_RESTRAINTS = new HashMap<>();

    /** Player UUID -> the exact list of restraint items we still expect to see dropped (and should cancel). */
    private static final Map<UUID, List<Item>> EXPECTED_DROPS = new HashMap<>();

    /** Player UUID -> where they died, used as a sanity check before cancelling a drop. */
    private static final Map<UUID, Vec3> DEATH_POSITIONS = new HashMap<>();

    private DeathRestraintHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeathCapture(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        captureOrdinaryRestraints(player);
    }

    private static void captureOrdinaryRestraints(ServerPlayer player) {
        if (isGameRuleEnabled(player, ModGameRules.FREE_AFTER_DEATH)) {
            // Vanilla Cuffed behaviour - do nothing.
            return;
        }

        IRestrainableCapability cap = CuffedAPI.Capabilities.getRestrainableCapability(player);
        if (cap == null || !cap.isRestrained()) {
            return;
        }

        CompoundTag snapshot = cap.serializeNBT();
        if (snapshot.isEmpty()) {
            return;
        }

        UUID uuid = player.getUUID();
        PENDING_RESTRAINTS.put(uuid, snapshot);
        DEATH_POSITIONS.put(uuid, player.position());

        List<Item> expected = new ArrayList<>(3);
        if (cap.getHeadRestraint() != null) {
            expected.add(cap.getHeadRestraint().getItem());
        }
        if (cap.getArmRestraint() != null) {
            expected.add(cap.getArmRestraint().getItem());
        }
        if (cap.getLegRestraint() != null) {
            expected.add(cap.getLegRestraint().getItem());
        }
        if (!expected.isEmpty()) {
            EXPECTED_DROPS.put(uuid, expected);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDeathCleanup(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // Whatever Cuffed's own (normal priority) death handling was going to
        // drop has already been dropped (and, if it matched, cancelled) by
        // this point. Anything left over is bookkeeping we no longer need -
        // vanilla's separate inventory-drop-on-death logic hasn't run yet at
        // this point, so clearing this now can never suppress an unrelated
        // drop later.
        EXPECTED_DROPS.remove(player.getUUID());
        DEATH_POSITIONS.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void onItemJoin(EntityJoinLevelEvent event) {
        if (EXPECTED_DROPS.isEmpty()) {
            return;
        }
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ItemEntity itemEntity)) {
            return;
        }

        ItemStack stack = itemEntity.getItem();
        if (!RestraintAPI.isRestraintItem(stack)) {
            return;
        }

        UUID matchedOwner = null;
        for (Map.Entry<UUID, List<Item>> entry : EXPECTED_DROPS.entrySet()) {
            List<Item> expected = entry.getValue();
            if (!expected.contains(stack.getItem())) {
                continue;
            }
            Vec3 deathPos = DEATH_POSITIONS.get(entry.getKey());
            if (deathPos != null && itemEntity.position().distanceToSqr(deathPos) > 16.0D) {
                // Too far from where that player died to plausibly be their
                // dropped restraint - leave it alone.
                continue;
            }
            expected.remove(stack.getItem());
            matchedOwner = entry.getKey();
            break;
        }

        if (matchedOwner != null) {
            event.setCanceled(true);
            List<Item> remaining = EXPECTED_DROPS.get(matchedOwner);
            if (remaining != null && remaining.isEmpty()) {
                EXPECTED_DROPS.remove(matchedOwner);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer newPlayer)) {
            return;
        }

        restoreOrdinaryRestraints(newPlayer);
    }

    private static void restoreOrdinaryRestraints(ServerPlayer newPlayer) {
        CompoundTag snapshot = PENDING_RESTRAINTS.remove(newPlayer.getUUID());
        if (snapshot == null || snapshot.isEmpty()) {
            return;
        }

        IRestrainableCapability cap = CuffedAPI.Capabilities.getRestrainableCapability(newPlayer);
        if (cap == null) {
            return;
        }

        // Same mechanism Cuffed itself uses to restore restraints on login/
        // clone: rebuilds the restraint objects from NBT and marks the
        // capability for a sync packet on the next server tick. No chat
        // output, no item drops.
        cap.deserializeNBT(snapshot);
    }

    private static boolean isGameRuleEnabled(ServerPlayer player, GameRules.Key<GameRules.BooleanValue> key) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            // Not something we should ever hit for a real ServerPlayer, but
            // fall back to vanilla Cuffed behaviour just in case.
            return true;
        }
        return serverLevel.getGameRules().getBoolean(key);
    }
}
