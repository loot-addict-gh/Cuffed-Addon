package com.example.cuffedaddon.enchantment;

import com.example.cuffedaddon.CuffedAddon;
import com.example.cuffedaddon.config.CuffedAddonServerConfig;
import com.example.cuffedaddon.init.ModEnchantments;
import com.example.cuffedaddon.mixin.AbstractRestraintItemDataAccessor;
import com.lazrproductions.cuffed.api.CuffedAPI;
import com.lazrproductions.cuffed.cap.RestrainableCapability;
import com.lazrproductions.cuffed.cap.base.IRestrainableCapability;
import com.lazrproductions.cuffed.restraints.RestraintAPI;
import com.lazrproductions.cuffed.restraints.base.AbstractArmRestraint;
import com.lazrproductions.cuffed.restraints.base.AbstractHeadRestraint;
import com.lazrproductions.cuffed.restraints.base.AbstractLegRestraint;
import com.lazrproductions.cuffed.restraints.base.AbstractRestraint;
import com.lazrproductions.cuffed.restraints.base.RestraintType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Restraint Gaze (1.5.15): hold your gaze on another player long enough and your
 * own restraints appear on them.
 *
 * <h2>The spec</h2>
 * Level 1 is 4 blocks for 10 seconds, level 2 is 8 blocks for 8 seconds, level 3
 * is 16 blocks for 6 seconds. Only the slots whose restraint actually carries the
 * enchantment are copied, and a slot the target has already filled is skipped in
 * silence.
 *
 * <h2>Decisions that were not spelled out, and what was chosen</h2>
 * <ul>
 *   <li><b>Mixed levels use the highest.</b> Gaze I on the head and Gaze III on
 *       the legs gives one 16-block, 6-second gaze that applies both restraints,
 *       rather than two independent timers racing each other. Two timers would
 *       have meant the legs landing six seconds in and the head four seconds
 *       later, which is worse to use and much worse to explain.</li>
 *   <li><b>The gaze must be unbroken.</b> Look away, let the target leave range,
 *       break line of sight, or switch to a different target, and the timer
 *       restarts from zero. There is no partial credit and nothing is
 *       remembered.</li>
 *   <li><b>Blocks break it.</b> {@code hasLineOfSight} is checked, so you cannot
 *       gaze through a wall.</li>
 *   <li><b>Aim is forgiving.</b> The test is how far the target's centre sits
 *       from the look ray, allowed to be off by half the target's own size plus
 *       {@link #AIM_TOLERANCE}. Holding pixel-exact aim for ten unbroken seconds
 *       would have been the whole difficulty of the enchantment, which is not what
 *       it is about. For a player that is 1.4 blocks of slack, which is a wide
 *       cone at 4 blocks and roughly "anywhere on their body" at 16 - forgiving
 *       where the range is short and demanding where it is long, which is the
 *       right way round.</li>
 *   <li><b>Nothing tells you it is building.</b> No sound, no bar, no particle
 *       while the timer runs - only Cuffed's ordinary equip sound when the
 *       restraint lands. Nothing was asked for and a progress cue would also warn
 *       the target. Easy to add.</li>
 * </ul>
 *
 * <h2>Copies are viral by default, and that is configurable</h2>
 * [stated] chose "copy Restraint Gaze too", so by default the restraint the
 * target receives carries every enchantment the original had, Gaze included -
 * they can gaze at someone else, indefinitely. The copy is built from the
 * wearer's own saved item data, so this costs nothing to support: a copy of a
 * copy is simply another copy.
 *
 * <p>{@code CuffedAddonServerConfig.GAZE_CHAIN} ("Restraint Gaze Chain", default
 * true) turns the chain off. When false the copy keeps every OTHER enchantment - Repellent,
 * Famine, whatever was on it - and loses only Restraint Gaze, so a chain stops
 * after one hop rather than the copy being stripped bare.
 *
 * <p>The copy is of the ITEM that was applied to the wearer, not of its current
 * worn state - so if the wearer was cuffed with a half-broken pair of handcuffs,
 * the target gets a half-broken pair. That falls out of using
 * {@code itemData} and is the more sensible of the two answers anyway.
 *
 * <p>Every copy is marked by {@link ConjuredRestraints} so that it vanishes on
 * removal instead of duplicating an item, which is what keeps the viral rule from
 * being an item duplication machine.
 *
 * <h2>Cuffed's own rules still apply</h2>
 * Applying goes through {@code RestraintAPI.canEquipRestriantItem} and
 * {@code RestrainableCapability#TryEquipRestraint}, the same calls this addon's
 * dispenser traps use. So Cuffed's {@code REQUIRE_LOW_HEALTH_TO_RESTRAIN} config,
 * if a world has it on, silently blocks a gaze against a healthy target exactly
 * as it blocks a pair of handcuffs - deliberately, since [stated] asked for "the
 * same requirements as normal".
 *
 * <h2>Real players only</h2>
 * Fake Players fake players are <b>not</b> valid targets and never were valid
 * gazers. They were briefly targetable at 1.5.15 behind a config toggle; [stated]
 * removed that at 1.5.17 - <i>"restraint gaze should never be in effect for fake
 * players"</i> - so the toggle now governs Repellent alone and this class does not
 * consult it at all. The candidate query is over {@code ServerPlayer} rather than
 * {@code LivingEntity}, which makes that structural rather than a check that could
 * be forgotten.
 */
@Mod.EventBusSubscriber(modid = CuffedAddon.MODID)
public final class RestraintGazeEvents {

    /** Range in blocks, indexed by enchantment level. Index 0 is unused. */
    private static final double[] RANGE = {0.0d, 4.0d, 8.0d, 16.0d};

    /** Unbroken gaze needed, in ticks, indexed by level: 10s, 8s, 6s. */
    private static final int[] HOLD_TICKS = {0, 200, 160, 120};

    /**
     * Ticks between samples (1.5.22).
     *
     * <p>{@link #onPlayerTick} used to run its full body every tick for every
     * player on the server, which meant a capability lookup plus a walk of all
     * four restraint slots' enchantment NBT twenty times a second per player -
     * paid in full by everyone NOT wearing Restraint Gaze, which is nearly
     * everyone. Its two sibling enchantment handlers already sample
     * ({@code IllusionEvents} every 5 ticks, {@code RepellentEvents} every 2);
     * this one was the odd one out.
     *
     * <p>{@link Gaze#ticks} is stepped by this same amount below rather than by
     * one, so the real-time hold this enchantment asks for is unchanged - only
     * the granularity of noticing that a stare broke, which goes from one tick
     * to five hundredths of a second either way. Gating on the player's own
     * {@code tickCount} also spreads the work across ticks instead of bunching
     * every player onto the same one.
     */
    private static final int INTERVAL_TICKS = 4;

    /** Slack the aim gets, in blocks, on top of half the target's own size. */
    private static final double AIM_TOLERANCE = 0.5d;

    /**
     * Who is currently staring at whom, and for how long.
     *
     * <p>A plain HashMap is safe here: every one of the three methods that touch
     * it ({@link #onPlayerTick}, {@link #onPlayerLoggedOut} and the reset inside
     * the tick) runs on the server thread. Nothing persists it - a six-to-ten
     * second timer is not worth saving, and losing it on a restart just means
     * starting the stare again.
     */
    private static final Map<UUID, Gaze> GAZES = new HashMap<>();

    /** Mutable on purpose - this is ticked up in place rather than reallocated. */
    private static final class Gaze {
        private UUID target;
        private int ticks;

        private Gaze(UUID target) {
            this.target = target;
            this.ticks = 1;
        }
    }

    private RestraintGazeEvents() {
    }

    // ------------------------------------------------------------------- tick

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % INTERVAL_TICKS != 0) {
            return;
        }

        IRestrainableCapability cap = CuffedAPI.Capabilities.getRestrainableCapability(player);
        int level = RestraintEnchantmentUtil.highestLevel(cap, ModEnchantments.RESTRAINT_GAZE.get());
        if (level <= 0) {
            GAZES.remove(player.getUUID());
            return;
        }

        double range = RANGE[Math.min(level, RANGE.length - 1)];
        int needed = HOLD_TICKS[Math.min(level, HOLD_TICKS.length - 1)];

        ServerPlayer target = findTarget(player, range);
        if (target == null) {
            GAZES.remove(player.getUUID());
            return;
        }

        Gaze gaze = GAZES.get(player.getUUID());
        if (gaze == null || !gaze.target.equals(target.getUUID())) {
            // New stare, or the gaze moved to somebody else: start over.
            GAZES.put(player.getUUID(), new Gaze(target.getUUID()));
            return;
        }

        gaze.ticks += INTERVAL_TICKS;
        if (gaze.ticks < needed) {
            return;
        }

        GAZES.remove(player.getUUID());
        applyGaze(player, target, cap);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        GAZES.remove(event.getEntity().getUUID());
    }

    // ----------------------------------------------------------- target search

    /**
     * The nearest thing this player is looking at within {@code range}, or null.
     *
     * <p>"Looking at" is measured as the perpendicular distance from the target's
     * centre to the player's look ray, rather than as an angle. That matters
     * because an angular tolerance which feels right at 4 blocks silently becomes
     * a very wide cone at 16, so level 3 would have been able to gaze at someone
     * it was not really looking at.
     *
     * <p>The geometry is deliberately written out by hand instead of calling
     * {@code AABB#clip}: it is four Vec3 operations, all of them names this
     * codebase or Cuffed already uses, where {@code clip}'s exact 1.20.1 signature
     * could not be verified from anything available in this sandbox - and a
     * mis-guessed API name here would cost a whole build round.
     *
     * <p>{@code along} is the distance to the target measured along the ray, so it
     * is also what "nearest" is judged on, and a target behind the player has a
     * negative value and is discarded before anything else is computed.
     */
    @Nullable
    private static ServerPlayer findTarget(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        AABB search = player.getBoundingBox().inflate(range);

        double rangeSqr = range * range;
        ServerPlayer best = null;
        double bestAlong = Double.MAX_VALUE;

        // ServerPlayer.class, not LivingEntity.class: real players are the only
        // thing this enchantment may ever act on, so the query itself says so.
        for (ServerPlayer candidate : player.level().getEntitiesOfClass(ServerPlayer.class, search,
                RestraintGazeEvents::isCandidate)) {
            if (candidate == player) {
                continue;
            }
            Vec3 centre = candidate.position().add(0.0d, candidate.getBbHeight() * 0.5d, 0.0d);
            Vec3 toTarget = centre.subtract(eye);
            if (toTarget.lengthSqr() > rangeSqr) {
                continue;
            }
            double along = toTarget.dot(look);
            if (along <= 0.0d || along > range) {
                continue;
            }
            // Pythagoras on the ray: the perpendicular leg, squared. Clamped at
            // zero because floating point can make it very slightly negative when
            // the target is dead centre.
            double perpSqr = Math.max(0.0d, toTarget.lengthSqr() - along * along);
            double tolerance = Math.max(candidate.getBbWidth() * 0.5d, candidate.getBbHeight() * 0.5d)
                    + AIM_TOLERANCE;
            if (perpSqr > tolerance * tolerance) {
                continue;
            }
            if (!player.hasLineOfSight(candidate)) {
                continue;
            }
            if (along < bestAlong) {
                bestAlong = along;
                best = candidate;
            }
        }
        return best;
    }

    private static boolean isCandidate(ServerPlayer entity) {
        return entity.isAlive() && !entity.isSpectator();
    }

    // ------------------------------------------------------------------ apply

    /**
     * Puts a copy of each gaze-enchanted restraint the wearer has onto the target.
     *
     * <p>Per-slot and independent: an occupied slot on the target is skipped and
     * the others still land, which is what "only the restraint with this
     * enchantment should also be applied [...] excluding the unenchanted ones"
     * plus "if a player Im looking at already has a restraint in that slot, the
     * enchantment should silently do nothing" add up to.
     */
    private static void applyGaze(ServerPlayer wearer, ServerPlayer target,
                                  @Nullable IRestrainableCapability wearerCap) {
        if (wearerCap == null) {
            return;
        }
        for (RestraintType slot : RestraintEnchantmentUtil.SLOTS) {
            AbstractRestraint worn = wearerCap.getRestraint(slot);
            if (worn == null) {
                continue;
            }
            if (RestraintEnchantmentUtil.levelOn(worn, ModEnchantments.RESTRAINT_GAZE.get()) <= 0) {
                continue;
            }
            ItemStack copy = conjureCopyOf(worn);
            if (copy.isEmpty()) {
                continue;
            }
            applyToPlayer(wearer, target, slot, copy);
        }
    }

    /**
     * A fresh, marked duplicate of the item a worn restraint was applied from.
     *
     * <p>Read straight out of {@code itemData} rather than through
     * {@code AbstractRestraint#saveToItemStack()}, for the reasons in
     * {@link AbstractRestraintItemDataAccessor} - chiefly that
     * {@code saveToItemStack} would write to the shared {@code ItemStack.EMPTY}
     * singleton if the data were ever unusable.
     */
    private static ItemStack conjureCopyOf(AbstractRestraint worn) {
        if (!(worn instanceof AbstractRestraintItemDataAccessor accessor)) {
            return ItemStack.EMPTY;
        }
        CompoundTag itemData = accessor.cuffedaddon$getItemData();
        if (itemData == null || itemData.isEmpty()) {
            return ItemStack.EMPTY;
        }
        // itemData.copy() is LOAD-BEARING, not defensive tidiness.
        // vanilla's ItemStack(CompoundTag) does NOT copy the tag it reads -
        // it is literally `this.tag = tag.getCompound("tag")` - so a stack built
        // straight from a worn restraint's itemData SHARES that restraint's NBT
        // object. Marking the copy as conjured then wrote the flag into the
        // WEARER's own restraint, and their real, enchanted restraint vanished on
        // removal along with the copies; stripping the enchantment below would
        // likewise have disenchanted theirs. [stated] hit the first of those at
        // 1.5.16. Copy the tag before anything touches it.
        ItemStack copy = ItemStack.of(itemData.copy());
        if (copy.isEmpty()) {
            return ItemStack.EMPTY;
        }
        copy.setCount(1);
        if (!CuffedAddonServerConfig.GAZE_CHAIN.get()) {
            RestraintEnchantmentUtil.stripEnchantment(copy, ModEnchantments.RESTRAINT_GAZE.get());
        }
        ConjuredRestraints.mark(copy);
        return copy;
    }

    private static boolean applyToPlayer(ServerPlayer wearer, ServerPlayer victim, RestraintType slot,
                                         ItemStack copy) {
        IRestrainableCapability capBase = CuffedAPI.Capabilities.getRestrainableCapability(victim);
        if (!(capBase instanceof RestrainableCapability cap)) {
            return false;
        }
        if (cap.isRestrained(slot)) {
            // Occupied: silently nothing, per [stated].
            return false;
        }
        if (!RestraintAPI.canEquipRestriantItem(copy, slot, victim, wearer)) {
            return false;
        }
        // A Bundle is only a head restraint while it is EMPTY, and Cuffed's own
        // BundleRestraint#canEquipRestraintItem does not actually enforce that -
        // it returns true on both branches - so the guard is duplicated here, the
        // same way RestraintDispenseBehavior duplicates it.
        if (copy.is(Items.BUNDLE) && BundleItem.getFullnessDisplay(copy) > 0) {
            return false;
        }

        AbstractRestraint restraint = RestraintAPI.getRestraintFromStack(copy, slot, victim, wearer);
        if (restraint == null) {
            return false;
        }
        if (slot == RestraintType.Arm && restraint instanceof AbstractArmRestraint arm) {
            return cap.TryEquipRestraint(victim, wearer, arm);
        }
        if (slot == RestraintType.Leg && restraint instanceof AbstractLegRestraint leg) {
            return cap.TryEquipRestraint(victim, wearer, leg);
        }
        if (slot == RestraintType.Head && restraint instanceof AbstractHeadRestraint head) {
            return cap.TryEquipRestraint(victim, wearer, head);
        }
        return false;
    }
}
