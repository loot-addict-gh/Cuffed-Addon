package com.example.cuffedaddon.picker;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Per-player state for Player Picker - lives on the CAPTURED player (the
 * target), not on the item. The item's own NBT only holds enough to
 * IDENTIFY who's captured (their profile, for display/rendering) and is
 * the link key this capability is looked up by; the actual "am I captured,
 * what do I go back to, and what am I currently anchored to" state lives
 * here, same division of responsibility ILiePose/IWallPose use for their
 * own restraint items vs pose capability.
 *
 * The "anchor" (ANCHOR_* fields) is deliberately NOT re-derived from
 * scratch every tick by searching the whole world - see PlayerPickerUtil's
 * own doc for why (performance), and for exactly how/when this gets
 * updated as the item moves between a player's inventory and a container.
 */
public interface IPlayerPicked {

    /**
     * CONTAINER is a fixed BlockPos container (chest, furnace, barrel,
     * hopper, ...) - a real BlockEntity, doesn't move. ENTITY_CONTAINER
     * (added for [stated]'s "put in a chest/hopper MINECART" report) is a
     * Container that's itself an Entity - a chest/hopper minecart, or any
     * other Entity implementing net.minecraft.world.Container (not
     * hardcoded to minecarts specifically) - tracked by that entity's own
     * UUID instead of a fixed position, resolved to its LIVE position every
     * tick so a moving cart is followed properly instead of leaving the
     * picked player stuck wherever the cart happened to be when it was
     * last (re)acquired.
     */
    enum AnchorType { NONE, PLAYER, CONTAINER, ENTITY_CONTAINER }

    boolean isPicked();

    void setPicked(boolean picked);

    /** GameType id (GameType#getId()/GameType#byId(int)) to restore on release. -1 = unset. */
    int getPreviousGameModeId();

    void setPreviousGameModeId(int id);

    AnchorType getAnchorType();

    void setAnchorType(AnchorType type);

    /** Valid only when getAnchorType() == PLAYER. */
    @Nullable
    UUID getAnchorPlayerUUID();

    void setAnchorPlayerUUID(@Nullable UUID uuid);

    /** Valid only when getAnchorType() == CONTAINER - the container block's own position. */
    @Nullable
    BlockPos getAnchorContainerPos();

    void setAnchorContainerPos(@Nullable BlockPos pos);

    /** Dimension the container anchor lives in, as a namespaced ResourceLocation string. Valid alongside a CONTAINER or ENTITY_CONTAINER anchor. */
    @Nullable
    String getAnchorDimension();

    void setAnchorDimension(@Nullable String dimension);

    /** Valid only when getAnchorType() == ENTITY_CONTAINER - the carrying entity's (e.g. a chest minecart's) own UUID. */
    @Nullable
    UUID getAnchorContainerEntityUUID();

    void setAnchorContainerEntityUUID(@Nullable UUID uuid);

    /**
     * Consecutive server ticks on which this capture's item could not be found
     * where it should be, and no re-acquire found it either. Reset to 0 the
     * moment an anchor resolves again.
     *
     * <p>Added in 1.4.40 for the grace-period auto-release (see
     * {@code PlayerPickerUtil#tickPickedPlayer} and
     * {@code CuffedAddonServerConfig.PLAYER_PICKER_AUTO_RELEASE_*}): [stated]
     * found that destroying a loaded Player Picker left the captured player
     * permanently stuck, and asked for a timeout as a backstop to the
     * {@code /unpick} command.
     *
     * <p><b>Only counts ticks where the item is definitively ABSENT</b>, never
     * ticks where it merely could not be checked (an anchor in an unloaded chunk
     * or another dimension). That distinction is the whole reason this is safe:
     * see {@code PlayerPickerUtil}'s own doc.
     */
    int getLostAnchorTicks();

    void setLostAnchorTicks(int ticks);

    CompoundTag serializeNBT();

    void deserializeNBT(CompoundTag tag);
}
