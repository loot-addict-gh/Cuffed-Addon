package com.example.cuffedaddon.client;

/**
 * Client-only mirror of the local player's own Shock Collar state, fed by
 * {@code ShockCollarSyncPacket}. Same tiny-holder shape as
 * {@code ClientPickedState}.
 *
 * <p>Also owns the local struggle cooldown/alternation bookkeeping, which is
 * client-side by nature: it exists so the wearer gets an instant audio cue and
 * so held-down mouse buttons don't flood the server, not as a rule the server
 * relies on. The server re-validates every attempt independently (see
 * {@code ShockCollarStrugglePacket}).
 */
public final class ClientCollaredState {

    private static volatile boolean collared = false;
    private static volatile int durability = 0;
    private static volatile int maxDurability = 1;

    /** Ticks until the next struggle attempt is allowed. Mirrors Cuffed's own breakCooldown. */
    private static float breakCooldown = 0.0f;

    /**
     * Which mouse button was used for the last successful attempt. Cuffed's
     * breakable restraints require ALTERNATING inputs
     * ({@code requireAlternateKeysToAttemptBreak}), so holding one button down
     * or mashing a single one gets you nowhere - you have to work at it.
     */
    private static int lastButton = -1;

    private ClientCollaredState() {
    }

    public static void set(boolean isCollared, int currentDurability, int max) {
        collared = isCollared;
        durability = currentDurability;
        maxDurability = Math.max(1, max);
        if (!isCollared) {
            breakCooldown = 0.0f;
            lastButton = -1;
        }
    }

    public static boolean isCollared() {
        return collared;
    }

    public static int getDurability() {
        return durability;
    }

    public static int getMaxDurability() {
        return maxDurability;
    }

    public static void tickCooldown() {
        if (breakCooldown > 0.0f) {
            breakCooldown -= 1.0f;
        }
    }

    public static boolean isCooldownOver() {
        return breakCooldown <= 0.0f;
    }

    public static void setCooldown(float ticks) {
        breakCooldown = ticks;
    }

    public static int getLastButton() {
        return lastButton;
    }

    public static void setLastButton(int button) {
        lastButton = button;
    }
}
