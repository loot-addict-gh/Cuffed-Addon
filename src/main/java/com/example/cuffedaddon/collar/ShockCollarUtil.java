package com.example.cuffedaddon.collar;

import com.example.cuffedaddon.capability.ModCapabilities;
import com.example.cuffedaddon.config.CuffedAddonServerConfig;
import com.example.cuffedaddon.init.ModEffects;
import com.example.cuffedaddon.items.ShockCollarItem;
import com.example.cuffedaddon.network.NetworkHandler;
import com.example.cuffedaddon.network.ShockCollarSyncPacket;
import com.lazrproductions.cuffed.api.CuffedAPI;
import com.lazrproductions.cuffed.cap.base.IRestrainableCapability;
import com.lazrproductions.cuffed.restraints.base.AbstractArmRestraint;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Every server-side state transition for the Shock Collar lives here, so the
 * item, the interact listener and the struggle packet all go through one place
 * and can't drift apart on what "applying"/"removing"/"breaking" means.
 *
 * <p>Audio deliberately matches Cuffed's own HANDCUFFS exactly, per [stated]'s
 * explicit request. Verified against both the GitHub source and a CFR decompile
 * of the real shipped Cuffed-1.20.1-1.3.15.jar (they agree - the source has not
 * drifted from the jar for this class):
 * <ul>
 *   <li>per struggle attempt, client-side {@code playNotifySound}:
 *       {@code SoundEvents.CHAIN_STEP}, volume 1.0, pitch random 0.9-1.1</li>
 *   <li>on break, server-side {@code level().playSound} at the wearer's
 *       blockPos: {@code SoundEvents.ITEM_BREAK}, {@code SoundSource.PLAYERS},
 *       volume 0.8, pitch random 0.9-1.1</li>
 * </ul>
 * (Note this addon's own {@code RopeArmsRestraint} has these two SWAPPED
 * relative to handcuffs - it plays ITEM_BREAK on the attempt and
 * LEASH_KNOT_BREAK on the break. Flagged to [stated]; left alone for now in
 * case it was deliberate for rope.)
 */
public final class ShockCollarUtil {

    /** How long one shock lasts / is refreshed to, in ticks. [stated]: "shocked for 2 seconds". */
    public static final int SHOCK_DURATION_TICKS = 40;

    /**
     * Delay from remote-release to the after-effects.
     *
     * <p>Was 20 ticks (one second) in 1.4.32, on the theory that nausea landing
     * mid-shock would read as random. [stated] tested it and asked for it
     * immediate instead, so this is 0: the dose is applied on the first server
     * tick after release. The field is kept rather than inlined because the
     * countdown machinery is what makes "on release, not during" work at all.
     */
    public static final int NAUSEA_DELAY_TICKS = 0;

    /** How long the nausea lasts. [stated] raised this 2s -> 6s -> 10s across testing rounds. */
    public static final int NAUSEA_DURATION_TICKS = 200;

    /** Hunger, applied alongside the nausea. [stated] raised this from 20s to 30s after testing. */
    public static final int HUNGER_DURATION_TICKS = 600;

    /**
     * The ARMING WINDOW - how long after a collar goes on before its remote
     * will fire. [stated]: "Id like there to be a 2 sec window between placing a
     * collar on a player and actually using the remote."
     *
     * <p>Applies to self-application as well as to collaring someone else, per
     * [stated]. During the window the remote FAILS SILENTLY - no toast, no chat
     * line, no sound, explicitly requested. See {@link #isArmed}.
     *
     * <p>Was 40 ticks (2 seconds) in 1.4.40; [stated] tested it and asked for
     * 1 second, so 20.
     */
    public static final int ARMING_WINDOW_TICKS = 20;
    /**
     * Shortest gap the server will accept between two struggle attempts, in
     * ticks. Matches the LOW end of the client's own randomised 20-40 tick
     * cooldown so honest players are never gated by it.
     */
    private static final int MIN_STRUGGLE_INTERVAL_TICKS = 20;

    private ShockCollarUtil() {
    }

    @Nullable
    public static ICollared getCollared(ServerPlayer player) {
        return player.getCapability(ModCapabilities.COLLARED).orElse(null);
    }

    public static boolean isCollared(ServerPlayer player) {
        ICollared cap = getCollared(player);
        return cap != null && cap.isCollared();
    }

    // ---------------------------------------------------------------- apply

    /**
     * Puts a collar on {@code target} and turns {@code collarStack} (the Shock
     * Collar the captor is holding) into that collar's BOUND REMOTE in place -
     * same single item, new NBT, which is what drives both the texture swap and
     * the italic-username display name. See {@link ShockCollarItem}.
     *
     * @return true if the collar was applied.
     */
    public static boolean applyCollar(ServerPlayer captor, ServerPlayer target, ItemStack collarStack) {
        if (!collarStack.is(com.example.cuffedaddon.init.ModItems.SHOCK_COLLAR.get())) {
            return false;
        }
        // An already-bound remote is not a collar to apply - it's the control
        // for one that already exists somewhere.
        if (ShockCollarItem.isBound(collarStack)) {
            return false;
        }
        ICollared cap = getCollared(target);
        if (cap == null || cap.isCollared()) {
            return false;
        }

        UUID bindingId = UUID.randomUUID();
        cap.setCollared(true);
        cap.setBindingId(bindingId);
        cap.setCaptorUUID(captor.getUUID());
        cap.setDurability(maxDurability());
        cap.setNauseaDelayTicks(-1);
        // Start the arming window. Both apply paths reach this method - the
        // EntityInteract listener for collaring someone else and Item#use's
        // crouch branch for self-application - so setting it here covers both
        // without either caller needing to know about it.
        cap.setArmingTicks(ARMING_WINDOW_TICKS);

        ShockCollarItem.bind(collarStack, bindingId, target.getGameProfile().getName());

        target.level().playSound(null, target.blockPosition(), SoundEvents.IRON_DOOR_CLOSE,
                SoundSource.PLAYERS, 0.7f, 1.4f);
        sync(target);
        return true;
    }

    // --------------------------------------------------------------- remove

    /**
     * Normal removal, by the collar's OWN bound remote. Reverts the remote
     * stack back to a plain unbound Shock Collar (default texture, default
     * name), per [stated]: "the item will return to the default texture and
     * default name".
     */
    public static boolean removeCollar(ServerPlayer wearer, ItemStack remoteStack) {
        ICollared cap = getCollared(wearer);
        if (cap == null || !cap.isCollared()) {
            return false;
        }
        UUID binding = cap.getBindingId();
        UUID stackBinding = ShockCollarItem.getBindingId(remoteStack);
        if (binding == null || stackBinding == null || !binding.equals(stackBinding)) {
            return false;
        }

        clearCollarState(cap);
        ShockCollarItem.unbind(remoteStack);

        wearer.removeEffect(ModEffects.ELECTRIZATION.get());
        wearer.level().playSound(null, wearer.blockPosition(), SoundEvents.IRON_DOOR_OPEN,
                SoundSource.PLAYERS, 0.7f, 1.4f);
        sync(wearer);
        return true;
    }

    // ---------------------------------------------------------------- break

    /**
     * Struggle-break.
     *
     * <p>What survives is governed by the "Drop Item When Broken" config, which
     * defaults to FALSE - [stated]'s original intent, "if its broken its
     * broken": the collar leaves nothing behind and its bound remote dies with
     * it, wherever in the world that remote happens to be.
     *
     * <p>Set to TRUE, the pair comes apart into its two components instead of
     * being destroyed: the wearer drops an Unbound Collar, and the bound remote
     * reverts to a plain Shock Remote item. Crafting them back together gives a
     * working Shock Collar again.
     *
     * <p>Either way the binding is permanently recorded so remotes the immediate
     * sweep can't reach settle themselves later - see
     * {@link RevokedBindingsSavedData} for why that's recorded rather than
     * lazily inferred.
     */
    public static void breakCollar(ServerPlayer wearer) {
        ICollared cap = getCollared(wearer);
        if (cap == null || !cap.isCollared()) {
            return;
        }
        UUID binding = cap.getBindingId();
        clearCollarState(cap);
        wearer.removeEffect(ModEffects.ELECTRIZATION.get());

        boolean salvage = CuffedAddonServerConfig.SHOCK_COLLAR_DROP_ITEM_WHEN_BROKEN.get();
        MinecraftServer server = wearer.getServer();
        if (binding != null && server != null) {
            revokeBinding(server, binding, salvage);
        }
        if (salvage) {
            dropUnboundCollar(wearer);
        }

        // Handcuffs' own break cue, exactly (see this class's doc).
        wearer.level().playSound(null, wearer.blockPosition(), SoundEvents.ITEM_BREAK,
                SoundSource.PLAYERS, 0.8f, wearer.getRandom().nextFloat() * 0.2f + 0.9f);
        sync(wearer);
    }

    /**
     * Records the binding as dead and settles every bound remote the server can
     * reach right now.
     *
     * <p>The immediate sweep covers ONLINE players' inventories, so the common
     * case - the captor standing right there - resolves instantly instead of on
     * that remote's next inventory tick. Everything it can't reach (chests,
     * ender chests, dropped item entities, offline players) is caught later by
     * the revocation check in {@link ShockCollarItem#inventoryTick}.
     */
    public static void revokeBinding(MinecraftServer server, UUID bindingId, boolean salvage) {
        RevokedBindingsSavedData.get(server).revoke(bindingId, salvage);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.isEmpty()) {
                    continue;
                }
                UUID stackBinding = ShockCollarItem.getBindingId(stack);
                if (stackBinding == null || !stackBinding.equals(bindingId)) {
                    continue;
                }
                player.getInventory().setItem(i, salvage
                        ? new ItemStack(com.example.cuffedaddon.init.ModItems.SHOCK_REMOTE.get())
                        : ItemStack.EMPTY);
                player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_BREAK,
                        SoundSource.PLAYERS, 0.8f, player.getRandom().nextFloat() * 0.2f + 0.9f);
            }
        }
    }

    /**
     * Admin removal, with no remote required - what
     * {@code /cuffed <player> remove Collar} calls.
     *
     * <p>This exists because every other way out needs something the wearer may
     * not have: the bound remote, or enough struggle durability. If a remote is
     * destroyed by accident (its holder dies and the item despawns) and
     * "Can Be Broken Out Of" is off, the collar would otherwise be permanent
     * with no way for an operator to intervene.
     *
     * <p><b>This one does NOT follow "Drop Item When Broken."</b> That setting
     * governs BREAKING, and nothing breaks here - an operator is unlocking a
     * collar. Per [stated], the command always hands back a single assembled,
     * unbound Shock Collar, so nothing is ever destroyed by using it. The bound
     * remote is revoked (salvage=false) precisely because the item handed back
     * already represents both halves; leaving the old remote alive as well
     * would duplicate it.
     *
     * <p>Uses the ordinary unequip cue rather than the break cue.
     *
     * @param recipient who gets the returned Shock Collar - the command's
     *                  sender. Null for a non-player source (console, command
     *                  block), in which case it drops at the wearer instead of
     *                  vanishing.
     * @return false if that player wasn't wearing a collar.
     */
    public static boolean forceRemoveCollar(ServerPlayer wearer, @Nullable ServerPlayer recipient) {
        ICollared cap = getCollared(wearer);
        if (cap == null || !cap.isCollared()) {
            return false;
        }
        UUID binding = cap.getBindingId();
        clearCollarState(cap);
        wearer.removeEffect(ModEffects.ELECTRIZATION.get());

        MinecraftServer server = wearer.getServer();
        if (binding != null && server != null) {
            revokeBinding(server, binding, false);
        }
        returnAssembledCollar(wearer, recipient);

        wearer.level().playSound(null, wearer.blockPosition(), SoundEvents.IRON_DOOR_OPEN,
                SoundSource.PLAYERS, 0.7f, 1.4f);
        sync(wearer);
        return true;
    }

    /**
     * Hands one unbound Shock Collar to the command's sender, falling back to
     * dropping it at the wearer when there is no sender player or no room.
     */
    private static void returnAssembledCollar(ServerPlayer wearer, @Nullable ServerPlayer recipient) {
        ItemStack stack = new ItemStack(com.example.cuffedaddon.init.ModItems.SHOCK_COLLAR.get());
        if (recipient != null) {
            if (!recipient.getInventory().add(stack)) {
                recipient.drop(stack, false);
            }
            return;
        }
        ItemEntity entity = new ItemEntity(wearer.level(),
                wearer.getX(), wearer.getY() + 0.6, wearer.getZ(), stack);
        entity.setDefaultPickUpDelay();
        wearer.level().addFreshEntity(entity);
    }

    /** Drops the collar half of the pair at the wearer, for the salvage case. */
    public static void dropUnboundCollar(ServerPlayer wearer) {
        ItemStack stack = new ItemStack(com.example.cuffedaddon.init.ModItems.UNBOUND_COLLAR.get());
        ItemEntity entity = new ItemEntity(wearer.level(),
                wearer.getX(), wearer.getY() + 0.6, wearer.getZ(), stack);
        entity.setDefaultPickUpDelay();
        wearer.level().addFreshEntity(entity);
    }

    private static void clearCollarState(ICollared cap) {
        cap.setCollared(false);
        cap.setBindingId(null);
        cap.setCaptorUUID(null);
        cap.setDurability(0);
        cap.setNauseaDelayTicks(-1);
        cap.setArmingTicks(0);
    }

    // -------------------------------------------------------------- struggle

    /**
     * One successful struggle attempt. Called from the struggle packet, which
     * has already been validated server-side - never trust the client's own
     * view of whether it may struggle.
     *
     * @return true if the attempt landed (so the client's audio cue was warranted).
     */
    public static boolean struggle(ServerPlayer wearer) {
        if (!canStruggle(wearer)) {
            return false;
        }
        ICollared cap = getCollared(wearer);
        if (cap == null) {
            return false;
        }
        // SERVER-SIDE RATE LIMIT, 1.5.14. Until now the only thing pacing a
        // struggle was ShockCollarStruggleEvents on the CLIENT - its 20-40 tick
        // cooldown, its button-alternation rule and its 50% roll. All three are
        // unreachable from here, so a modified client that simply sent this
        // packet every tick decremented durability every tick and opened a
        // 50-durability collar in about two and a half seconds.
        //
        // The gate is deliberately a PLAIN MINIMUM INTERVAL equal to the
        // client's own SHORTEST cooldown, not a second 50% roll: a legitimate
        // client never sends faster than 20 ticks apart, so this is invisible
        // to honest play and the confirmed feel of the feature is bit-for-bit
        // unchanged. It does leave a modified client somewhat faster than an
        // honest one (it skips the roll), just no longer unboundedly so - see
        // README-inprogress for the stricter variant and why it was not taken.
        if (cap.getStruggleCooldownTicks() > 0) {
            return false;
        }
        cap.setStruggleCooldownTicks(MIN_STRUGGLE_INTERVAL_TICKS);

        cap.setDurability(cap.getDurability() - 1);
        if (cap.getDurability() <= 0) {
            breakCollar(wearer);
        } else {
            sync(wearer);
        }
        return true;
    }

    /**
     * The full server-side precondition for working the collar loose.
     *
     * <p>[stated]'s design: the wearer spams mouse clicks while holding one of
     * Cuffed's cutlery items (fork/spoon/knife). Two things that buys: the collar
     * never shares an input channel with ARM restraints (which already consume
     * raw mouse clicks), and you can't get at your own neck while your arms are
     * bound.
     *
     * <p><b>The arm-restraint gate is {@code AllowItemUse()}, not "is any arm
     * restraint present".</b> The rule is therefore: you can work the collar
     * exactly when your arms are free enough to use an item at all. Those two
     * formulations differ in exactly one place - Cuffed's
     * {@code ShacklesArmsRestraint} is the only arm restraint that returns
     * {@code AllowItemUse() == true}; every other one (Handcuffs, Fuzzy
     * Handcuffs, Duck Tape, and this addon's own Rope and Straitjacket) returns
     * false. [stated] was asked about this specific case and chose it
     * deliberately: <i>"if someones wearing shackles and holding cutlery, they
     * can struggle out of the shock collar"</i>. So shackles are the one arm
     * restraint loose enough to let you reach your own collar.
     *
     * <p>This check has to be explicit here because the struggle listener
     * watches RAW mouse input, which Cuffed does not suppress - being
     * arm-restrained does not stop the clicks arriving.
     */
    public static boolean canStruggle(ServerPlayer wearer) {
        ICollared cap = getCollared(wearer);
        if (cap == null || !cap.isCollared()) {
            return false;
        }
        if (!CuffedAddonServerConfig.SHOCK_COLLAR_CAN_BE_BROKEN_OUT_OF.get()) {
            return false;
        }
        if (!isHoldingCutlery(wearer)) {
            return false;
        }
        IRestrainableCapability restrainable = CuffedAPI.Capabilities.getRestrainableCapability(wearer);
        if (restrainable == null) {
            return true;
        }
        AbstractArmRestraint arms = restrainable.getArmRestraint();
        return arms == null || arms.AllowItemUse();
    }

    /** Cuffed's three cutlery items, checked in BOTH hands. */
    public static boolean isHoldingCutlery(ServerPlayer wearer) {
        return isCutlery(wearer.getMainHandItem()) || isCutlery(wearer.getOffhandItem());
    }

    public static boolean isCutlery(ItemStack stack) {
        return stack.is(com.lazrproductions.cuffed.init.ModItems.FORK.get())
                || stack.is(com.lazrproductions.cuffed.init.ModItems.SPOON.get())
                || stack.is(com.lazrproductions.cuffed.init.ModItems.KNIFE.get());
    }

    // ----------------------------------------------------------------- shock

    /**
     * Whether this collar's arming window has elapsed, so its remote may fire.
     *
     * <p>See {@link #ARMING_WINDOW_TICKS}. A collar with no capability or no
     * collar on is trivially "not armed" - callers treat that the same as the
     * window still running, which is the safe direction.
     */
    public static boolean isArmed(ServerPlayer wearer) {
        ICollared cap = getCollared(wearer);
        return cap != null && cap.isCollared() && cap.getArmingTicks() <= 0;
    }

    /**
     * Applies (or refreshes) the shock. Called every tick the bound remote's
     * right-click is held, which is what makes the 2-second window roll forward
     * continuously instead of expiring mid-hold.
     *
     * <p>Does nothing at all while the collar's ARMING WINDOW is still running -
     * silently, per [stated]: no message, no sound, no partial effect. Gating
     * here rather than at the call site means every present and future way of
     * firing a remote inherits the window automatically.
     */
    public static void shock(ServerPlayer wearer) {
        if (!isArmed(wearer)) {
            return;
        }
        wearer.addEffect(new MobEffectInstance(ModEffects.ELECTRIZATION.get(),
                SHOCK_DURATION_TICKS, 0, false, true, true));
        // Any pending nausea from a previous release is cancelled - a new hold
        // started before it landed, so it would have fired mid-shock.
        ICollared cap = getCollared(wearer);
        if (cap != null) {
            cap.setNauseaDelayTicks(-1);
        }
    }

    /**
     * How long an Arrow of Electrization shocks for, in seconds.
     *
     * <p>Read through here rather than straight from the config so the item's
     * tooltip and the arrow itself can never disagree.
     */
    public static int arrowElectrizationSeconds() {
        return Math.max(0, CuffedAddonServerConfig.ARROW_ELECTRIZATION_SECONDS.get());
    }

    /**
     * Shocks a target with no collar involved - what an Arrow of Electrization
     * does on hit (1.6.1).
     *
     * <h2>Why this is not {@link #shock}</h2>
     * {@code shock} is the REMOTE's entry point and carries the remote's rules:
     * it refuses unless the victim is wearing an armed collar, and it is called
     * every tick of a held right-click so its 2-second window rolls forward. An
     * arrow has neither property - it lands once, on anyone - so it gets its own
     * door rather than a flag threaded through that one.
     *
     * <h2>"its after effects"</h2>
     * [stated]'s spec was "apply electrization for 5 secs and its after
     * effects", and the after-effects split in two:
     *
     * <ul>
     *   <li>The slowness, weakness and mining fatigue need nothing here -
     *       {@code ElectrizationEffect} re-applies all three every tick it runs
     *       and they trail off on their own moments after it ends.</li>
     *   <li>The nausea and hunger do. With a collar those fire when the remote
     *       is RELEASED, which is the moment the shock stops, so the faithful
     *       translation for a fixed-length arrow shock is to schedule them for
     *       when the duration runs out rather than to land them on top of it.
     *       That is what the delay below is.</li>
     * </ul>
     *
     * <p>The scheduling rides the collar capability's existing countdown, which
     * is why {@link #tickCollared} no longer gates it on actually being
     * collared. That also makes this half player-only: a mob has no such
     * capability. The shock itself is not - {@code ElectrizationEffect} is
     * written against {@code LivingEntity} throughout, damage included, so the
     * arrow shocks whatever it hits.
     */
    public static void electrify(LivingEntity target) {
        int ticks = arrowElectrizationSeconds() * 20;
        if (ticks <= 0) {
            return;
        }
        target.addEffect(new MobEffectInstance(ModEffects.ELECTRIZATION.get(),
                ticks, 0, false, true, true));
        if (target instanceof ServerPlayer player) {
            ICollared cap = getCollared(player);
            if (cap != null) {
                cap.setNauseaDelayTicks(ticks);
            }
        }
    }

    /**
     * Called once when the remote's right-click is released - arms the delayed
     * nausea.
     *
     * <p>Also gated on {@link #isArmed}: a press that happened entirely inside
     * the arming window dealt no shock, so it must not produce the after-effects
     * of one either. Without this, clicking the remote immediately after
     * collaring someone would skip the shock but still make them nauseous.
     */
    public static void onShockReleased(ServerPlayer wearer) {
        if (!isArmed(wearer)) {
            return;
        }
        ICollared cap = getCollared(wearer);
        if (cap != null && cap.isCollared()) {
            cap.setNauseaDelayTicks(NAUSEA_DELAY_TICKS);
        }
    }

    /**
     * Server tick for one collared player - runs the arming-window countdown
     * and then the delayed-nausea countdown.
     */
    public static void tickCollared(ServerPlayer wearer) {
        ICollared cap = getCollared(wearer);
        if (cap == null) {
            return;
        }
        if (cap.isCollared()) {
            int arming = cap.getArmingTicks();
            if (arming > 0) {
                cap.setArmingTicks(arming - 1);
            }
            int struggleCooldown = cap.getStruggleCooldownTicks();
            if (struggleCooldown > 0) {
                cap.setStruggleCooldownTicks(struggleCooldown - 1);
            }
        }
        // The nausea countdown deliberately runs whether or not a collar is on
        // (1.6.1). It used to sit behind the isCollared() guard above, which was
        // fine while the Shock Collar was the only thing that could ever set it -
        // the Arrow of Electrization now sets it too, and its victim usually has
        // no collar. Nothing changes for a collared player: the countdown is -1
        // unless something armed it, so this is the same code reached by a wider
        // set of players, not new behaviour for the old set.
        int pending = cap.getNauseaDelayTicks();
        if (pending < 0) {
            return;
        }
        if (pending == 0) {
            // Always applied. The toggle that briefly guarded this is gone:
            // [stated] traced the black-screen problem to the Solas shader, not
            // to this mod or to vanilla, and nausea behaves normally without it.
            wearer.addEffect(new MobEffectInstance(MobEffects.CONFUSION,
                    NAUSEA_DURATION_TICKS, 0, false, true, true));
            wearer.addEffect(new MobEffectInstance(MobEffects.HUNGER,
                    HUNGER_DURATION_TICKS, 0, false, true, true));
            cap.setNauseaDelayTicks(-1);
        } else {
            cap.setNauseaDelayTicks(pending - 1);
        }
    }

    // ------------------------------------------------------------- plumbing

    /** Finds the online player wearing the collar this binding id belongs to. */
    @Nullable
    public static ServerPlayer findWearer(MinecraftServer server, UUID bindingId) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ICollared cap = getCollared(player);
            if (cap != null && cap.isCollared() && bindingId.equals(cap.getBindingId())) {
                return player;
            }
        }
        return null;
    }

    public static int maxDurability() {
        return Math.max(1, CuffedAddonServerConfig.SHOCK_COLLAR_DURABILITY.get());
    }

    /**
     * Pushes collar state to every client that can see this player, plus the
     * player themselves. Trackers need it because the worn collar is visible on
     * other players and capabilities don't sync on their own; see
     * {@link ShockCollarSyncPacket} for the full reasoning.
     */
    public static void sync(ServerPlayer wearer) {
        ICollared cap = getCollared(wearer);
        boolean collared = cap != null && cap.isCollared();
        int durability = cap == null ? 0 : cap.getDurability();
        NetworkHandler.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> wearer),
                new ShockCollarSyncPacket(wearer.getId(), collared, durability, maxDurability()));
    }

    /** Shared random-pitch helper matching Cuffed's own 0.9-1.1 spread. */
    public static float randomPitch(ServerPlayer player) {
        return Mth.randomBetween(player.getRandom(), 0.9f, 1.1f);
    }
}
