package com.example.cuffedaddon.client;

/**
 * Tiny client-only holder for "am I (the local player) currently picked up
 * by a Player Picker" - set by PlayerPickedSelfSyncPacket, read by
 * CuffedAddonKeybinds' onClientTick to suppress the local crouch key while
 * true. See PlayerPickedSelfSyncPacket's own doc for why this needs to be
 * client-side state at all, not just a server-side fix.
 */
public class ClientPickedState {

    private static volatile boolean picked = false;

    private ClientPickedState() {
    }

    public static void setPicked(boolean value) {
        picked = value;
    }

    public static boolean isPicked() {
        return picked;
    }
}
