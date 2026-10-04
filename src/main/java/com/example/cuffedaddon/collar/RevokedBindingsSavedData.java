package com.example.cuffedaddon.collar;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The world's list of collar binding ids that are DEAD - i.e. the collar that
 * minted them has been broken out of - so that any remote still carrying one
 * can destroy itself the next time it's ticked.
 *
 * <p><b>Why a persisted revocation list instead of just checking "is anyone
 * wearing this collar".</b> When a collar breaks, [stated]'s spec says the
 * bound remote must break too, wherever it is. Scanning online players'
 * inventories at break time (which {@code ShockCollarUtil#revokeBinding}
 * still does, for the common case) can't reach a remote sitting in a chest, in
 * an ender chest, on the ground as an item entity, or in an OFFLINE player's
 * inventory. The obvious lazy alternative - have each remote check every tick
 * whether its wearer still exists and self-destruct if not - is actively wrong:
 * it would destroy every remote whose wearer merely LOGGED OFF, since an
 * offline player's capability isn't reachable either. A player logging out is
 * not the same event as a collar breaking, and the remote must survive the
 * first and not the second.
 *
 * <p>So the break itself records the fact, permanently, and remotes consult
 * that record. One UUID per broken collar; this grows only when a collar is
 * actually struggled out of, so it stays negligible in practice.
 *
 * <p>Stored on the OVERWORLD's {@code DimensionDataStorage} specifically (not
 * per-level) so the list is global - a remote carried into the Nether must see
 * a revocation recorded in the Overworld. Same approach
 * {@code ReinforcedPositionsSavedData}/{@code HearUnmuffledSavedData} already
 * use in this addon.
 */
public class RevokedBindingsSavedData extends SavedData {

    private static final String DATA_NAME = "cuffedaddon_revoked_collar_bindings";

    /** Bindings whose remote should be destroyed outright. */
    private final Set<UUID> revokedBroken = new HashSet<>();

    /**
     * Bindings whose remote should turn back into a plain Shock Remote item
     * rather than be destroyed - i.e. the collar was broken while the
     * "Drop Item When Broken" config was on.
     */
    private final Set<UUID> revokedSalvaged = new HashSet<>();

    public RevokedBindingsSavedData() {
    }

    public static RevokedBindingsSavedData create() {
        return new RevokedBindingsSavedData();
    }

    private static void readInto(CompoundTag tag, String key, Set<UUID> into) {
        ListTag list = tag.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            try {
                into.add(UUID.fromString(list.getString(i)));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public static RevokedBindingsSavedData load(CompoundTag tag) {
        RevokedBindingsSavedData data = new RevokedBindingsSavedData();
        readInto(tag, "Revoked", data.revokedBroken);
        readInto(tag, "Salvaged", data.revokedSalvaged);
        return data;
    }

    private static ListTag writeFrom(Set<UUID> from) {
        ListTag list = new ListTag();
        for (UUID uuid : from) {
            list.add(StringTag.valueOf(uuid.toString()));
        }
        return list;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.put("Revoked", writeFrom(revokedBroken));
        tag.put("Salvaged", writeFrom(revokedSalvaged));
        return tag;
    }

    public static RevokedBindingsSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            // Should be unreachable for a running server, but never hand back
            // null - a missing store must not crash a remote's inventoryTick.
            return new RevokedBindingsSavedData();
        }
        return overworld.getDataStorage().computeIfAbsent(
                RevokedBindingsSavedData::load,
                RevokedBindingsSavedData::create,
                DATA_NAME
        );
    }

    /**
     * @param salvage true when the remote should become a plain Shock Remote
     *                instead of being destroyed.
     */
    public void revoke(UUID bindingId, boolean salvage) {
        Set<UUID> target = salvage ? revokedSalvaged : revokedBroken;
        if (target.add(bindingId)) {
            setDirty();
        }
    }

    public boolean isRevoked(UUID bindingId) {
        return revokedBroken.contains(bindingId) || revokedSalvaged.contains(bindingId);
    }

    /** Only meaningful when {@link #isRevoked} is true for the same id. */
    public boolean shouldSalvage(UUID bindingId) {
        return revokedSalvaged.contains(bindingId);
    }

    /**
     * Takes a revocation notice down once the remote it was for has acted on it
     * (1.5.22).
     *
     * <h2>Why this exists</h2>
     * This store is a noticeboard, not a record. The collar that ends has no way
     * to reach its remote - it could be in a chest, an ender chest, or the
     * inventory of somebody who logged off last week - so instead the server
     * posts the binding id here and every remote checks the board from its own
     * {@code inventoryTick} until it finds itself.
     *
     * <p>Nothing used to take a notice back down, so the board grew by one uuid
     * for every collar that had ever ended, for the life of the world, and was
     * written into the save on every autosave and read back on every load. Small
     * - roughly 36 bytes an entry - but unbounded, which is the part worth
     * fixing.
     *
     * <h2>The one assumption</h2>
     * That a binding id belongs to exactly ONE remote, so the first remote to
     * read the notice is also the last that needs it. That holds by construction:
     * {@code ShockCollarUtil} generates a fresh {@code UUID.randomUUID()} per
     * collar application and writes it to that collar and that remote only.
     *
     * <p>It stops holding if a bound remote is ever DUPLICATED - middle-click in
     * creative, or a {@code /give} with copied NBT - because the copy would never
     * see the notice the original already consumed, and would stay live as a
     * remote for a collar that no longer exists. [stated] has accepted that:
     * nobody plays this in creative, and a duplicated remote is not expected
     * behaviour, so a bug there is acceptable. Recorded here so the trade is
     * visible if that ever changes.
     *
     * @return true if a notice was actually taken down.
     */
    public boolean consume(UUID bindingId) {
        // Both sets unconditionally, as two statements rather than one
        // short-circuiting expression: a binding only ever lands in one of them
        // (see revoke above), but clearing both means a hand-edited or
        // half-written store cannot leave a stale notice in the other one.
        boolean wasBroken = revokedBroken.remove(bindingId);
        boolean wasSalvaged = revokedSalvaged.remove(bindingId);
        if (wasBroken || wasSalvaged) {
            setDirty();
            return true;
        }
        return false;
    }
}
