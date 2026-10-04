package com.example.cuffedaddon.gamerule;

import net.minecraft.world.level.GameRules;

/**
 * Registers this addon's /gamerule rules.
 *
 * - freeAfterDeath (default true): when set to false, a player's ordinary
 *   restraints (anything worn in the head/arm/leg restraint slots -
 *   handcuffs, shackles, bundles, duct tape, etc.) survive death and
 *   respawn instead of being freed and dropped. Read server-side only
 *   (DeathRestraintHandler), so it needs no client sync.
 *
 * - fixClearBars (default false): when set to true, corrects a bug in
 *   Cuffed's own ReinforcedBarsBlock where a solid bar directly above a
 *   reinforced_bars_gap ("window") always renders using the middle texture,
 *   and one directly below can only ever render as top or bottom, never
 *   middle -- regardless of what's really above/below the whole stack. Read
 *   server-side only (ReinforcedBarsColumnFix). Off by default and kept
 *   behind a toggle since it depends on Cuffed's current, undocumented
 *   internals rather than a public API -- if a future Cuffed update fixes
 *   this properly (or changes the logic in a way that makes this correction
 *   wrong), it can be turned off without needing a new addon build.
 *
 * REMOVED in 1.4.41: bundleMuffle (default false) made a Bundle worn as a head
 * restraint muffle chat the way Cuffed's own duct tape does. [stated] asked for
 * it to go - it had no use. It was the only opt-in muffle here; every other
 * muffling head restraint in this addon (Rope, the 4 combos, Straitjacket) is
 * unconditional by design, so nothing else needed the gamerule's client-side
 * mirror. Deleting it took `BundleMuffleSync`, `BundleMuffleSyncPacket` and
 * `ClientGameRuleState` with it - that last one existed solely to cache this
 * one boolean on the client. **If a future rule ever needs a client-visible
 * value again, that trio is the pattern to copy back**: a GameRules
 * `BooleanValue.create(default, callback)` broadcasting on change, plus a
 * join-time send, plus a static client cache.
 */
public final class ModGameRules {

    public static final GameRules.Key<GameRules.BooleanValue> FREE_AFTER_DEATH = GameRules.register(
            "freeAfterDeath",
            GameRules.Category.PLAYER,
            GameRules.BooleanValue.create(true)
    );

    public static final GameRules.Key<GameRules.BooleanValue> FIX_CLEAR_BARS = GameRules.register(
            "fixClearBars",
            GameRules.Category.MISC,
            GameRules.BooleanValue.create(false)
    );

    private ModGameRules() {
    }

    /**
     * No-op used purely to force this class (and therefore the static
     * GameRules.register calls above) to load as early as possible.
     */
    public static void init() {
    }
}
