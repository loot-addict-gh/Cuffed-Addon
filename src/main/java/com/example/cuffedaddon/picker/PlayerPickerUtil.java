package com.example.cuffedaddon.picker;

import com.example.cuffedaddon.capability.ModCapabilities;
import com.example.cuffedaddon.config.CuffedAddonServerConfig;
import com.example.cuffedaddon.items.PlayerPickerItem;
import com.example.cuffedaddon.network.NetworkHandler;
import com.example.cuffedaddon.network.PlayerPickedSelfSyncPacket;
import com.lazrproductions.cuffed.api.CuffedAPI;
import com.lazrproductions.cuffed.cap.RestrainableCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Anchor tracking deliberately does NOT do a full-world search every tick
 * to find wherever the item currently is - that would mean scanning every
 * loaded container block entity, server-wide, every single tick, for every
 * currently-picked player. Instead the capability remembers its last known
 * anchor (a player, or a container block position) and each tick just
 * re-verifies THAT specific spot still holds the item - cheap. Only when
 * that check fails (the item moved) does this fall back to a search, and
 * even then a bounded one: all online players' inventories (cheap, bounded
 * by player count) first, then a small-radius block-entity scan centered
 * on the picked player's own current position (which tracks their LAST
 * confirmed anchor, since they're pinned there every tick) - covers a
 * hopper nudging the item one block over, which is the only way an
 * already-anchored item moves without a player directly handling it.
 *
 * Known limitation, not solved here: if the item is carried far outside
 * that small radius in a single tick with nobody's inventory holding it in
 * between (not achievable through normal hopper automation, which only
 * ever moves one block at a time - could only happen via another mod or
 * command), the picked player just stays frozen at their last confirmed
 * spot until something brings the item back within range. Flag if this is
 * ever actually hit in practice.
 *
 * <h2>1.4.40 - "absent" vs "can't tell", and the grace-period auto-release</h2>
 * [stated] discovered the feature's worst failure mode: <i>"if the picked
 * player item is destroyed, the picked player is impossible to free"</i> - the
 * item is the only handle on a capture, so destroying it left someone in
 * spectator with no route back. Three things address that, of which two live in
 * this class: the item is now fire-resistant (see {@code ModItems}), there is an
 * operator command ({@code /unpick}, see {@code ModCommands}, which calls
 * {@link #releasePicked} and {@link #locateAndClearPicker} here), and there is
 * an optional unattended backstop - if the item stays missing for a configured
 * time, the capture releases itself in place.
 *
 * <p><b>The backstop only ever counts ticks where the item is definitively
 * ABSENT, never ticks where it simply could not be checked.</b> [stated]'s
 * condition for wanting it at all was that it must not free anyone whose item
 * still exists - specifically not one parked in a chest, sitting in an AFK
 * player's inventory, or riding a moving container. Those three all RESOLVE
 * every tick, so they reset the counter and can never trip it. The dangerous
 * cases are the ones where a lookup fails for a reason other than the item being
 * gone, and every one of those is classified {@code unverifiable} instead, which
 * RESETS the counter exactly as a successful lookup does:
 * <ul>
 *   <li>the anchor's chunk is not loaded;</li>
 *   <li>the anchor's dimension cannot be resolved (a container in a level that
 *       is not currently loaded - so a chest in the Nether with nobody there
 *       parks the capture indefinitely rather than releasing it);</li>
 *   <li>the item is lying on the ground as an {@link ItemEntity} - it exists,
 *       it is simply not something the anchor system can hang a position on, so
 *       the capture waits for someone to pick it back up.</li>
 * </ul>
 * So the counter only reaches its threshold after an unbroken run of ticks that
 * each positively established the item is not there.
 * And even after the counter does run out, {@link #itemStillExistsAnywhere} does
 * one final wider sweep before anything is released.
 *
 * <p>The one residual gap, stated plainly rather than papered over: a loaded
 * picker thrown further than {@link #WIDE_SWEEP_RADIUS} blocks from the picked
 * player, in a loaded chunk, with nobody holding it, is indistinguishable here
 * from a destroyed one and will eventually auto-release (if the feature is
 * enabled at all - it defaults off). Closing that properly would mean making a
 * dropped {@code ItemEntity} a first-class anchor type, which is a bigger change
 * than this round asked for.
 */
public class PlayerPickerUtil {

    private static final int REACQUIRE_RADIUS = 4;
    private static final int REACQUIRE_SCAN_INTERVAL_TICKS = 20;

    /**
     * Reach of the one-off confirmation sweep run just before an auto-release,
     * and of the {@code /unpick} item hunt. Much wider than
     * {@link #REACQUIRE_RADIUS} because unlike that per-tick scan this runs at
     * most once per grace period per capture (and, for {@code /unpick}, once per
     * command), so it can afford to look properly.
     *
     * <p>Flattened rather than cubic - 24 out, 8 up and down - because the cost
     * is the number of block positions visited and a cube gets expensive fast:
     * this box is ~41k positions, where a 24-radius cube would be ~118k and a
     * 32-radius cube ~275k. Containers worth finding are near the picked player's
     * own height, so height buys much less than reach does.
     */
    private static final int WIDE_SWEEP_RADIUS = 24;
    private static final int WIDE_SWEEP_HEIGHT = 8;

    /**
     * Result of one anchor lookup. Three states, not two - see this class's doc
     * for why that distinction is what makes the auto-release safe:
     * <ul>
     *   <li>a position - the item was found, put the picked player here;</li>
     *   <li>{@link #LOST} - the item is provably not where it should be;</li>
     *   <li>{@link #UNKNOWN} - the lookup could not tell, so absence must NOT be
     *       inferred from it.</li>
     * </ul>
     *
     * <p>A plain nested class rather than a record on purpose: nothing else in
     * this codebase uses records, and there is no compiler available in the
     * environment these changes are written in to confirm the project's source
     * level accepts one.
     */
    private static final class AnchorLookup {
        static final AnchorLookup LOST = new AnchorLookup(null, false);
        static final AnchorLookup UNKNOWN = new AnchorLookup(null, true);

        @Nullable
        private final Vec3 position;
        private final boolean unverifiable;

        private AnchorLookup(@Nullable Vec3 position, boolean unverifiable) {
            this.position = position;
            this.unverifiable = unverifiable;
        }

        static AnchorLookup at(Vec3 position) {
            return new AnchorLookup(position, false);
        }

        @Nullable
        Vec3 position() {
            return position;
        }

        boolean unverifiable() {
            return unverifiable;
        }
    }

    @Nullable
    public static IPlayerPicked get(ServerPlayer player) {
        return player.getCapability(ModCapabilities.PLAYER_PICKED).orElse(null);
    }

    public static boolean isPicked(ServerPlayer player) {
        IPlayerPicked cap = get(player);
        return cap != null && cap.isPicked();
    }

    /**
     * Preconditions: target has BOTH an arm and a leg restraint equipped
     * (any kind - head restraint status irrelevant, per [stated]'s explicit
     * spec), target isn't already picked, and the item in hand is currently
     * empty (can't pick a second player into an already-loaded picker -
     * capacity of exactly one, see PlayerPickerItem's own doc on "1
     * durability").
     */
    public static boolean tryPick(ServerPlayer actor, ServerPlayer target, ItemStack stack) {
        if (PlayerPickerItem.hasPickedPlayer(stack)) return false;
        if (isPicked(target)) return false;

        RestrainableCapability targetCap =
                (RestrainableCapability) CuffedAPI.Capabilities.getRestrainableCapability(target);
        if (!targetCap.armsRestrained() || !targetCap.legsRestrained()) return false;

        IPlayerPicked cap = get(target);
        if (cap == null) return false;

        cap.setPicked(true);
        cap.setPreviousGameModeId(target.gameMode.getGameModeForPlayer().getId());
        cap.setAnchorType(IPlayerPicked.AnchorType.PLAYER);
        cap.setAnchorPlayerUUID(actor.getUUID());
        cap.setAnchorContainerPos(null);
        cap.setAnchorContainerEntityUUID(null);
        cap.setAnchorDimension(null);

        target.setGameMode(GameType.SPECTATOR);
        target.setDeltaMovement(Vec3.ZERO);

        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> target),
                new PlayerPickedSelfSyncPacket(true));

        PlayerPickerItem.setPickedPlayer(stack, target);
        return true;
    }

    /** Called every server tick for every currently-picked ONLINE player. */
    public static void tickPickedPlayer(MinecraftServer server, ServerPlayer picked, IPlayerPicked cap) {
        if (picked.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
            picked.setGameMode(GameType.SPECTATOR);
        }
        picked.setDeltaMovement(Vec3.ZERO);
        picked.fallDistance = 0.0F;

        // [stated] reported crouching while picked makes their screen buggy -
        // and confirmed the FIRST attempt (only resetting isShiftKeyDown) did
        // NOT fix it, "the exact same issue happens". isShiftKeyDown is only
        // the raw input flag; the actual visible crouch POSE is a separate
        // synced field (Entity#pose), set independently by Player#aiStep
        // based on that flag plus collision checks - resetting just the flag
        // doesn't reliably un-set an already-applied Pose.CROUCHING. This
        // round also forces the pose itself back to STANDING every tick,
        // same "reassert every tick" pattern as the spectator gamemode/
        // zero-velocity lines above - both are plain, real, non-mixin public
        // Entity/LivingEntity methods.
        if (picked.isShiftKeyDown()) {
            picked.setShiftKeyDown(false);
        }
        if (picked.getPose() != net.minecraft.world.entity.Pose.STANDING) {
            picked.setPose(net.minecraft.world.entity.Pose.STANDING);
        }

        AnchorLookup lookup = resolveAnchor(server, picked, cap);
        Vec3 target = lookup.position();
        if (target == null) {
            // Anchor currently unresolvable - stay put, retry next tick.
            //
            // An "unverifiable" tick RESETS the grace-period counter rather than
            // merely pausing it, which matters more than it looks: the item-on-
            // the-ground check only runs on the throttled scan tick (1 in 20), so
            // pausing would let the other 19 keep counting and a dropped picker
            // would still eventually trip the timer. Resetting means a single
            // "the item exists / can't tell" observation per second is enough to
            // hold the capture open indefinitely. The counter therefore only ever
            // reaches the threshold after an unbroken run of ticks that each
            // positively established the item is not there.
            if (lookup.unverifiable()) {
                cap.setLostAnchorTicks(0);
            } else {
                tickLostAnchor(server, picked, cap);
            }
            return;
        }
        cap.setLostAnchorTicks(0);

        // REPLACED THIS ROUND: the previous fix here (teleportTo with
        // RelativeMovement.ROTATION, meant to leave the picked player's own
        // look alone) still left [stated] with a locked camera while their
        // captor moved, plus a new symptom - crouching snapped the camera
        // to face south. Both point at teleportTo itself: ANY overload
        // (relative rotation flags or not) sends the picked player's own
        // client a position packet that requires a teleport-confirmation
        // round trip, and doing that essentially every tick (since a
        // walking captor moves almost every tick) keeps their client in a
        // near-permanent resync cycle - consistent with both symptoms.
        //
        // Fixed properly now by not teleporting the picked player at all:
        // they RIDE a small invisible PlayerPickerAnchorEntity that gets
        // repositioned via plain #setPos (an ordinary tracked-entity
        // update, no confirmation round trip, never touches rotation) -
        // vanilla's own passenger plumbing (the same mechanism a boat/
        // horse/minecart passenger already relies on for smooth position
        // following with full free look) takes care of moving the picked
        // player along with it every tick. See PlayerPickerAnchorEntity's
        // own doc for the full reasoning.
        Entity vehicle = picked.getVehicle();
        if (vehicle instanceof PlayerPickerAnchorEntity anchor && !anchor.isRemoved()) {
            anchor.setPos(target.x, target.y, target.z);
        } else {
            // First tick after being picked, or the old anchor entity
            // didn't survive something (server restart, dimension change,
            // desync) - drop whatever vehicle (if any) is currently set
            // and mount a fresh one at the resolved target.
            if (vehicle != null) {
                picked.stopRiding();
            }
            PlayerPickerAnchorEntity freshAnchor = new PlayerPickerAnchorEntity(picked.level(), target);
            picked.level().addFreshEntity(freshAnchor);
            picked.startRiding(freshAnchor, true);
        }
    }

    /**
     * Stops the picked player riding its carrier entity (if any) and
     * discards that entity - called from every release path (self-release
     * useOn, inventory-logout auto-release, the defensive death-clear) so
     * no orphaned PlayerPickerAnchorEntity is ever left behind in the
     * world.
     */
    public static void dismountAndDiscardAnchor(ServerPlayer picked) {
        Entity vehicle = picked.getVehicle();
        picked.stopRiding();
        if (vehicle instanceof PlayerPickerAnchorEntity) {
            vehicle.discard();
        }
    }

    // ------------------------------------------------------------- releasing

    /**
     * THE single release path, extracted in 1.4.40.
     *
     * <p>Restores the captured player's gamemode, gets them off the anchor
     * entity, clears every field of their capability and tells their client. It
     * deliberately does NOT move them and does NOT touch any item - callers
     * differ on both, and there are now four of them: the item's own
     * right-click-a-block release (which teleports them to the clicked block),
     * the owner-logout auto-release (in place), {@code /unpick} (in place), and
     * the grace-period auto-release (in place).
     *
     * <p>This used to be copy-pasted between the first two, which is precisely
     * how the {@code lostAnchorTicks} field would have ended up reset in one
     * path and not the other. One method, every caller.
     *
     * @return false if that player was not actually picked.
     */
    public static boolean releasePicked(ServerPlayer picked) {
        IPlayerPicked cap = get(picked);
        if (cap == null || !cap.isPicked()) {
            return false;
        }

        GameType restoreMode = cap.getPreviousGameModeId() >= 0
                ? GameType.byId(cap.getPreviousGameModeId())
                : GameType.SURVIVAL;
        picked.setGameMode(restoreMode);
        dismountAndDiscardAnchor(picked);

        cap.setPicked(false);
        cap.setPreviousGameModeId(-1);
        cap.setAnchorType(IPlayerPicked.AnchorType.NONE);
        cap.setAnchorPlayerUUID(null);
        cap.setAnchorContainerPos(null);
        cap.setAnchorContainerEntityUUID(null);
        cap.setAnchorDimension(null);
        cap.setLostAnchorTicks(0);

        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> picked),
                new PlayerPickedSelfSyncPacket(false));
        return true;
    }

    /**
     * What {@code /unpick <target>} runs.
     *
     * <p>Frees the target whether or not their item still exists, per [stated]:
     * <i>"if the item is gone, itll just free the player. If the item is still
     * existent, itll be turned to a normal Player Picker."</i> So it releases
     * first and then, separately, tries to find the item and empty it - the
     * release must never be contingent on finding the item, since the whole
     * reason this command exists is the case where there is nothing to find.
     *
     * <p>Inventory and existing Cuffed restraints are untouched, as with every
     * other release path - only gamemode and the anchor ever changed.
     *
     * @return true if the target was picked and has been freed.
     */
    public static boolean tryUnpick(MinecraftServer server, ServerPlayer picked) {
        if (!releasePicked(picked)) {
            return false;
        }
        locateAndClearPicker(server, picked);
        return true;
    }

    /**
     * Hunts down the Player Picker holding this capture and empties it back to a
     * plain, reusable Player Picker.
     *
     * <p>Looks everywhere a stack can reasonably be reached from here: every
     * online player's main inventory, offhand and ender chest, then every
     * container block and every entity (chest minecarts, and dropped stacks
     * lying on the ground) within {@link #WIDE_SWEEP_RADIUS} of the picked
     * player. Keeps going after the first hit rather than stopping, so a
     * duplicated stack - possible via creative or another mod - cannot leave a
     * second picker still displaying a name it no longer owns.
     *
     * @return true if at least one stack was found and cleared.
     */
    public static boolean locateAndClearPicker(MinecraftServer server, ServerPlayer picked) {
        UUID pickedUUID = picked.getUUID();
        boolean cleared = false;

        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            cleared |= clearMatchingIn(online.getInventory(), pickedUUID);
            cleared |= clearMatchingIn(online.getEnderChestInventory(), pickedUUID);
        }

        ServerLevel level = (ServerLevel) picked.level();
        BlockPos center = picked.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-WIDE_SWEEP_RADIUS, -WIDE_SWEEP_HEIGHT, -WIDE_SWEEP_RADIUS),
                center.offset(WIDE_SWEEP_RADIUS, WIDE_SWEEP_HEIGHT, WIDE_SWEEP_RADIUS))) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof Container container) {
                cleared |= clearMatchingIn(container, pickedUUID);
            }
        }

        AABB sweepBox = new AABB(center).inflate(WIDE_SWEEP_RADIUS);
        for (Entity entity : level.getEntities((Entity) null, sweepBox)) {
            if (entity instanceof Container container) {
                cleared |= clearMatchingIn(container, pickedUUID);
            }
            if (entity instanceof ItemEntity item && matches(item.getItem(), pickedUUID)) {
                ItemStack stack = item.getItem();
                PlayerPickerItem.clearPickedPlayer(stack);
                // setItem, not just mutating the stack in place: the carried
                // stack is synched entity data, so clients keep showing the old
                // name and the captured player's skin until it is re-set.
                item.setItem(stack);
                cleared = true;
            }
        }

        return cleared;
    }

    /** Empties every stack in this container that holds the given capture. */
    private static boolean clearMatchingIn(Container container, UUID pickedUUID) {
        boolean cleared = false;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (matches(stack, pickedUUID)) {
                PlayerPickerItem.clearPickedPlayer(stack);
                container.setChanged();
                cleared = true;
            }
        }
        return cleared;
    }

    // -------------------------------------------------- grace-period backstop

    /**
     * One tick on which the item was provably absent. Advances the counter and,
     * once it runs past the configured delay, releases the capture in place -
     * but only after {@link #itemStillExistsAnywhere} has had a last look.
     *
     * <p>Disabled by default ({@code Auto Release When Item Lost}), in which case
     * this does nothing at all: no counting, and in particular no sweep, so a
     * server that doesn't want the feature pays nothing for it.
     */
    private static void tickLostAnchor(MinecraftServer server, ServerPlayer picked, IPlayerPicked cap) {
        if (!CuffedAddonServerConfig.PLAYER_PICKER_AUTO_RELEASE_ENABLED.get()) {
            return;
        }

        int elapsed = cap.getLostAnchorTicks() + 1;
        int threshold = Math.max(1, CuffedAddonServerConfig.PLAYER_PICKER_AUTO_RELEASE_SECONDS.get()) * 20;
        if (elapsed < threshold) {
            cap.setLostAnchorTicks(elapsed);
            return;
        }

        // Timer is up. Before freeing anyone, look properly - the per-tick check
        // is deliberately cheap and narrow, and being wrong here means releasing
        // someone whose item was fine all along.
        if (itemStillExistsAnywhere(server, picked)) {
            cap.setLostAnchorTicks(0);
            return;
        }

        releasePicked(picked);
    }

    /**
     * The confirmation sweep: is this capture's item anywhere we can see?
     *
     * <p>Same ground as {@link #locateAndClearPicker} but read-only. Runs at most
     * once per grace period per capture, so the wide radius is affordable.
     */
    private static boolean itemStillExistsAnywhere(MinecraftServer server, ServerPlayer picked) {
        UUID pickedUUID = picked.getUUID();

        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (containerHoldsCapture(online.getInventory(), pickedUUID)
                    || containerHoldsCapture(online.getEnderChestInventory(), pickedUUID)) {
                return true;
            }
        }

        ServerLevel level = (ServerLevel) picked.level();
        BlockPos center = picked.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-WIDE_SWEEP_RADIUS, -WIDE_SWEEP_HEIGHT, -WIDE_SWEEP_RADIUS),
                center.offset(WIDE_SWEEP_RADIUS, WIDE_SWEEP_HEIGHT, WIDE_SWEEP_RADIUS))) {
            if (level.isLoaded(pos) && containerHolds(level, pos, pickedUUID)) {
                return true;
            }
        }

        AABB sweepBox = new AABB(center).inflate(WIDE_SWEEP_RADIUS);
        for (Entity entity : level.getEntities((Entity) null, sweepBox)) {
            if (entityContainerHolds(entity, pickedUUID)) {
                return true;
            }
            if (entity instanceof ItemEntity item && matches(item.getItem(), pickedUUID)) {
                return true;
            }
        }

        return false;
    }

    private static boolean containerHoldsCapture(Container container, UUID pickedUUID) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (matches(container.getItem(i), pickedUUID)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Where the picked player should be this tick, or why we don't know.
     *
     * <p>Structured as "re-verify the remembered anchor, else re-acquire", as
     * before; what 1.4.40 adds is that each failure path says whether it
     * actually PROVED the item absent. Anything that couldn't check returns
     * {@link AnchorLookup#UNKNOWN}, which parks the capture indefinitely instead
     * of counting towards the auto-release.
     */
    private static AnchorLookup resolveAnchor(MinecraftServer server, ServerPlayer picked, IPlayerPicked cap) {
        UUID pickedUUID = picked.getUUID();

        // Set when a check could not be CARRIED OUT, as opposed to being carried
        // out and coming back negative. Note none of these branches return
        // early: the re-acquire attempts below are still worth making (the item
        // may simply have been moved somewhere we can see), and the flag is only
        // consulted at the very end, if nothing at all was found.
        boolean unverifiable = false;

        if (cap.getAnchorType() == IPlayerPicked.AnchorType.PLAYER && cap.getAnchorPlayerUUID() != null) {
            ServerPlayer owner = server.getPlayerList().getPlayer(cap.getAnchorPlayerUUID());
            if (owner != null && inventoryHolds(owner, pickedUUID)) {
                return AnchorLookup.at(owner.position().add(0, 1, 0));
            }
            // An OFFLINE anchor holder is deliberately NOT treated as
            // unverifiable, even though their inventory can't be inspected. A
            // logout while actually holding the item auto-releases the capture
            // (PlayerPickerEvents#onPlayerLoggedOut), so if we are still here
            // with that player gone, the item demonstrably wasn't in their hands
            // - it is elsewhere, and the sweeps are what decide where.
        } else if (cap.getAnchorType() == IPlayerPicked.AnchorType.CONTAINER && cap.getAnchorContainerPos() != null) {
            ServerLevel level = resolveLevel(server, cap.getAnchorDimension());
            BlockPos pos = cap.getAnchorContainerPos();
            if (level == null || !level.isLoaded(pos)) {
                // Either the dimension isn't loaded (a chest in the Nether with
                // nobody there) or the chunk isn't. The chest is almost certainly
                // still holding it; we just cannot read the block entity to say
                // so, and must not conclude the item is gone from that.
                unverifiable = true;
            } else if (containerHolds(level, pos, pickedUUID)) {
                return AnchorLookup.at(containerAnchorPosition(pos));
            }
        } else if (cap.getAnchorType() == IPlayerPicked.AnchorType.ENTITY_CONTAINER && cap.getAnchorContainerEntityUUID() != null) {
            ServerLevel level = resolveLevel(server, cap.getAnchorDimension());
            Entity carrier = level == null ? null : level.getEntity(cap.getAnchorContainerEntityUUID());
            if (carrier == null) {
                // getEntity only sees LOADED entities, so a missing carrier means
                // the minecart is out of range, not provably destroyed.
                unverifiable = true;
            } else if (entityContainerHolds(carrier, pickedUUID)) {
                return AnchorLookup.at(entityContainerAnchorPosition(carrier));
            }
        }

        // Anchor invalid or never set - try to re-acquire.
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (inventoryHolds(online, pickedUUID)) {
                cap.setAnchorType(IPlayerPicked.AnchorType.PLAYER);
                cap.setAnchorPlayerUUID(online.getUUID());
                cap.setAnchorContainerPos(null);
                cap.setAnchorContainerEntityUUID(null);
                cap.setAnchorDimension(null);
                return AnchorLookup.at(online.position().add(0, 1, 0));
            }
        }

        // Bounded radius container re-scan, throttled - see class doc. Checks
        // both fixed BlockPos containers (chest, furnace, ...) AND
        // Container-implementing ENTITIES (chest/hopper minecart, and any
        // other such entity, not hardcoded to minecarts) - see
        // AnchorType.ENTITY_CONTAINER's own doc for why the entity case
        // exists ([stated] reported a picked player left behind wherever a
        // chest/hopper minecart was at pickup time instead of following it
        // while moving, since a BlockPos-only anchor can never track a
        // moving entity).
        if (picked.tickCount % REACQUIRE_SCAN_INTERVAL_TICKS == 0) {
            ServerLevel level = (ServerLevel) picked.level();
            BlockPos center = picked.blockPosition();
            for (BlockPos pos : BlockPos.betweenClosed(
                    center.offset(-REACQUIRE_RADIUS, -REACQUIRE_RADIUS, -REACQUIRE_RADIUS),
                    center.offset(REACQUIRE_RADIUS, REACQUIRE_RADIUS, REACQUIRE_RADIUS))) {
                if (level.isLoaded(pos) && containerHolds(level, pos, pickedUUID)) {
                    cap.setAnchorType(IPlayerPicked.AnchorType.CONTAINER);
                    cap.setAnchorContainerPos(pos.immutable());
                    cap.setAnchorContainerEntityUUID(null);
                    cap.setAnchorDimension(level.dimension().location().toString());
                    cap.setAnchorPlayerUUID(null);
                    return AnchorLookup.at(containerAnchorPosition(pos));
                }
            }

            AABB scanBox = new AABB(center).inflate(REACQUIRE_RADIUS);
            for (Entity entity : level.getEntities((Entity) null, scanBox)) {
                if (entityContainerHolds(entity, pickedUUID)) {
                    cap.setAnchorType(IPlayerPicked.AnchorType.ENTITY_CONTAINER);
                    cap.setAnchorContainerEntityUUID(entity.getUUID());
                    cap.setAnchorContainerPos(null);
                    cap.setAnchorDimension(level.dimension().location().toString());
                    cap.setAnchorPlayerUUID(null);
                    return AnchorLookup.at(entityContainerAnchorPosition(entity));
                }
                // A loaded picker lying on the ground. Not an anchor - there is
                // no AnchorType for a dropped stack - but it is proof the item
                // has NOT been destroyed, which is what the auto-release cares
                // about. So the capture parks until someone picks it back up.
                if (entity instanceof ItemEntity item && matches(item.getItem(), pickedUUID)) {
                    unverifiable = true;
                    break;
                }
            }
        }

        return unverifiable ? AnchorLookup.UNKNOWN : AnchorLookup.LOST;
    }

    /**
     * [stated]'s explicit request: sitting a whole block above a container
     * (Vec3.atCenterOf(pos.above()), the old value here) put the picked
     * player's camera noticeably higher than the container itself. Dropped
     * one full block - Vec3.atCenterOf(pos) instead of pos.above() - so the
     * camera sits directly above the container block rather than floating
     * a block above it. The PLAYER-inventory anchor (owner.position().add(0,
     * 1, 0), in resolveAnchor above) is UNCHANGED per that same
     * request - only the container case moved down.
     */
    private static Vec3 containerAnchorPosition(BlockPos pos) {
        return Vec3.atCenterOf(pos);
    }

    private static boolean inventoryHolds(ServerPlayer player, UUID pickedUUID) {
        for (ItemStack stack : player.getInventory().items) {
            if (matches(stack, pickedUUID)) return true;
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (matches(stack, pickedUUID)) return true;
        }
        return false;
    }

    private static boolean containerHolds(Level level, BlockPos pos, UUID pickedUUID) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof Container container)) return false;
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (matches(container.getItem(i), pickedUUID)) return true;
        }
        return false;
    }

    /**
     * Same idea as containerHolds, but for a Container that's itself an
     * Entity (a chest/hopper minecart, or anything else implementing
     * net.minecraft.world.Container - deliberately not narrowed to
     * minecart classes specifically, so any current or future
     * entity-based container works the same way with zero extra code).
     */
    private static boolean entityContainerHolds(Entity entity, UUID pickedUUID) {
        if (!(entity instanceof Container container)) return false;
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (matches(container.getItem(i), pickedUUID)) return true;
        }
        return false;
    }

    /**
     * Mirrors containerAnchorPosition's "directly above the block itself,
     * not floating a block above it" request, adapted for an entity
     * carrier: sits the picked player right at the top of the carrying
     * entity's own current bounding box (e.g. just above a chest
     * minecart's lid) rather than a fixed block-height offset, so it stays
     * correct whatever the carrying entity's actual size is.
     */
    private static Vec3 entityContainerAnchorPosition(Entity entity) {
        return entity.position().add(0, entity.getBbHeight(), 0);
    }

    private static boolean matches(ItemStack stack, UUID pickedUUID) {
        if (!PlayerPickerItem.hasPickedPlayer(stack)) return false;
        var profile = PlayerPickerItem.getPickedProfile(stack);
        return profile != null && pickedUUID.equals(profile.getId());
    }

    @Nullable
    private static ServerLevel resolveLevel(MinecraftServer server, @Nullable String dimension) {
        if (dimension == null) return null;
        ResourceLocation loc = ResourceLocation.tryParse(dimension);
        if (loc == null) return null;
        return server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, loc));
    }
}
