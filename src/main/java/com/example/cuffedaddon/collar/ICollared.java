package com.example.cuffedaddon.collar;

import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Per-player state for the Shock Collar - the addon's own FOURTH restraint
 * slot, deliberately separate from Cuffed's three (head/arms/legs).
 *
 * <p><b>Why a custom capability rather than a Curios slot.</b> Cuffed's own
 * {@code IRestrainableCapability} is hardcoded to exactly three getters
 * ({@code getHeadRestraint}/{@code getArmRestraint}/{@code getLegRestraint}),
 * so there is no fourth slot to register into and the collar can't be a real
 * Cuffed restraint without stealing the head slot from Sleep Mask/Bundle/
 * Straitjacket. The other candidate was a Curios slot, which would have given
 * persistence, a GUI and a render hook for free - and Curios 5.14.1's
 * {@code ICurio#canUnequip} really is enforced ({@code DynamicStackHandler#extractItem},
 * and {@code CurioSlot extends SlotItemHandler} whose {@code mayPickup}/{@code remove}
 * both route through it), so "wearer can't take it off" was achievable there.
 * It was rejected because {@code curios} is a NON-MANDATORY dependency of this
 * addon (see mods.toml) and every Curios-touching class is isolated behind a
 * {@code ModList.get().isLoaded("curios")} guard precisely so the mod works
 * without it. A headline item that simply doesn't exist without Curios would
 * have forced that dependency to mandatory. Secondary reasons: the slot would
 * sit permanently in everyone's Curios GUI, and every third-party curio-
 * swapping/backpack/dump-on-death mod would get to touch a restraint.
 *
 * <p>This capability lives on the WEARER. The bound remote's own NBT holds
 * only {@link #getBindingId()} - the shared random UUID minted at apply time
 * that links a specific remote to a specific collaring. That mirrors how
 * Player Picker splits state between the captured player's capability and the
 * item's NBT (see {@code IPlayerPicked}), for the same reason: the item can be
 * anywhere in the world, so it can't be the source of truth for player state.
 *
 * <p>Note {@link #getBindingId()} is minted fresh per application rather than
 * reusing the wearer's UUID: a remote must stop working the instant ITS
 * collaring ends, even if the same player gets collared again later by someone
 * else's remote.
 */
public interface ICollared {

    boolean isCollared();

    void setCollared(boolean collared);

    /**
     * Remaining struggle durability. Counts DOWN from
     * {@code CuffedAddonServerConfig.SHOCK_COLLAR_DURABILITY} to 0, at which
     * point the collar breaks (and takes its bound remote with it).
     */
    int getDurability();

    void setDurability(int durability);

    /**
     * The random UUID minted when this collar was applied, written into the
     * bound remote's NBT as well. A remote only works on a wearer whose
     * capability carries the SAME binding id. Null when not collared.
     */
    @Nullable
    UUID getBindingId();

    void setBindingId(@Nullable UUID bindingId);

    /** Who applied the collar, for damage attribution. May be the wearer themselves (self-application). */
    @Nullable
    UUID getCaptorUUID();

    void setCaptorUUID(@Nullable UUID captorUUID);

    /**
     * Server-side countdown to the delayed nausea dose, started when the
     * remote's right-click is RELEASED. -1 means nothing pending.
     *
     * <p>The delay exists because nausea landing mid-shock reads as random;
     * firing it a second after release makes it legible as an after-effect.
     */
    int getNauseaDelayTicks();

    void setNauseaDelayTicks(int ticks);

    /**
     * Ticks left before this collar's remote will actually fire - the ARMING
     * WINDOW. Counts down to 0, at which point the collar is live. 0 or below
     * means armed.
     *
     * <p>Added in 1.4.40 at [stated]'s request: applying a collar and shocking
     * were the same gesture held a moment too long, so putting one on someone
     * shocked them instantly. The window covers SELF-application too, per
     * [stated] - the same accidental-double-fire exists there.
     *
     * <p>Deliberately a countdown stored on the capability rather than an
     * "applied at tick N" timestamp compared against {@code tickCount}: a
     * player's tickCount is not a wall clock shared between client and server
     * (see the 1.4.39 overlay-flash desync), and a countdown serialises through
     * death/respawn carry-over for free with the rest of this capability.
     */
    int getArmingTicks();

    void setArmingTicks(int ticks);

    /**
     * Ticks remaining before this player's next struggle attempt will be
     * ACCEPTED by the server. Added in 1.5.14 - until then the struggle rate
     * limit existed only on the client, so a modified client could send the
     * struggle packet every tick and break a collar in seconds. See
     * {@code ShockCollarUtil#struggle}.
     */
    int getStruggleCooldownTicks();

    void setStruggleCooldownTicks(int ticks);

    CompoundTag serializeNBT();

    void deserializeNBT(CompoundTag tag);
}
