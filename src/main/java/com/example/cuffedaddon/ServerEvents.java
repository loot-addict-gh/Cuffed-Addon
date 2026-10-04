package com.example.cuffedaddon;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = CuffedAddon.MODID)
public class ServerEvents {

    /**
     * ROUND (prior session): [stated] reported the Pet Patroller item from
     * "Leashable Collars" (a third-party mod, decompiled to confirm this)
     * causes an unrelated PREVIOUS message to show up attached via the
     * hearUnmuffled reveal, on a message that was never actually paired
     * with it.
     *
     * Root cause, confirmed by decompiling that mod's jar: its collar
     * "speech muffled/silenced" modes are enforced by a mixin
     * (ServerGamePacketListenerImplMixin#playercollars$blockRestrictedSpeech)
     * that injects at the HEAD of handleChat and calls ci.cancel() - this
     * cancels the vanilla ServerboundChatPacket entirely, before
     * ServerChatEvent ever fires, for BOTH its "muffled" and "silenced"
     * modes (it doesn't forward a garbled version the way Cuffed's own
     * muffling does - it just drops the message outright).
     *
     * But OUR OriginalChatPacket (sent by the CLIENT, over this addon's own
     * network channel, ahead of/alongside the real chat packet - see that
     * class's own doc) is a completely separate packet that Pet
     * Patroller's mixin never touches, since it only targets handleChat/
     * handleChatCommand/handlePlayerInput. So queueOriginal below still
     * runs and pushes that message's original text onto PENDING even
     * though the corresponding muffled chat message never actually
     * broadcasts - the entry that was supposed to be consumed by
     * onServerChat below is orphaned instead, sitting in the queue
     * indefinitely (ArrayDeque never expires anything on its own). The
     * NEXT time this player successfully sends ANY chat message - even a
     * later, entirely unrelated one, once the collar restriction is lifted
     * or from Cuffed's own muffling instead - pollFirst() pops that stale
     * leftover and wrongly pairs it with the new message.
     *
     * Fixed by timestamping each queued entry and discarding (not using)
     * anything older than STALE_THRESHOLD_MILLIS when popping in
     * onServerChat - a genuine pairing's two packets (OriginalChatPacket +
     * the real chat packet) are sent by the client essentially
     * simultaneously, so a large gap can only mean the entry's own
     * matching chat message never actually arrived (blocked by Pet
     * Patroller, or in principle any other mod/mechanism that can drop a
     * chat packet after the client already sent it). Discarding it instead
     * of using it just means that ONE message doesn't get the "(original)"
     * reveal treatment, matching normal Cuffed muffling behavior with no
     * reveal available - a far better failure mode than showing the wrong
     * text entirely.
     */
    private static final long STALE_THRESHOLD_MILLIS = 5000L;

    private record PendingOriginal(String text, long queuedAtMillis) {
    }

    private static final Map<UUID, Deque<PendingOriginal>> PENDING = new ConcurrentHashMap<>();

    public static void queueOriginal(UUID senderId, String original) {
        PENDING.computeIfAbsent(senderId, k -> new ArrayDeque<>())
                .addLast(new PendingOriginal(original, System.currentTimeMillis()));
    }

    /**
     * ROUND (this session): [stated]'s friend was getting repeatedly kicked
     * ("Chat message validation failure" / "Received chat packet with
     * missing or invalid signature" - confirmed from the server log
     * [stated] attached), specifically only while hearUnmuffled was
     * enabled and the friend was chatting - never the player who applied
     * the restraint.
     *
     * Root cause: this method used to call event.setCanceled(true) and
     * then manually re-broadcast a custom-built message to every player
     * itself. That's a well-documented footgun with Minecraft's signed-chat
     * system (1.19.1+): cancelling ServerChatEvent swallows the player's
     * signed message instead of letting it go through vanilla's real
     * broadcast path, which is what actually advances that player's
     * "last seen / acknowledged messages" chain. Every cancelled message is
     * signature debt that never gets paid off - it accumulates with every
     * single chat message sent while this feature is doing its job, and
     * once enough debt piles up the server forcibly disconnects that
     * player for failing chat validation. This explains why only the
     * MUFFLED player (whose real signed packet was the one being
     * swallowed) ever got kicked, never the person applying restraints.
     *
     * Fixed by NEVER cancelling the real chat event at all - the normal
     * muffled message now always broadcasts through vanilla's own path,
     * completely untouched, keeping the sender's signature chain
     * perfectly healthy no matter how many messages get sent. The
     * "(original)" reveal is now a SEPARATE, additional system message
     * (unsigned - sendSystemMessage never touches the signed-chat chain at
     * all) sent only to hearUnmuffled-enabled viewers, appearing as its
     * own line right after the real muffled message rather than appended
     * to it inline. This does mean the reveal is now its own line instead
     * of trailing the original message - flag if that's not wanted, but it
     * was the only way found to keep this ack-safe.
     */
    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        ServerPlayer sender = event.getPlayer();
        String muffled = event.getMessage().getString();

        Deque<PendingOriginal> queue = PENDING.get(sender.getUUID());
        String original = null;
        if (queue != null) {
            long now = System.currentTimeMillis();
            PendingOriginal entry;
            // Discard (don't use) anything stale - see STALE_THRESHOLD_MILLIS's
            // own doc above for why an old entry can only be orphaned, never
            // a genuine late pairing.
            while ((entry = queue.pollFirst()) != null) {
                if (now - entry.queuedAtMillis() <= STALE_THRESHOLD_MILLIS) {
                    original = entry.text();
                    break;
                }
            }
        }

        if (original == null || original.equals(muffled)) {
            return;
        }

        // Deliberately NOT event.setCanceled(true) here - see this method's
        // own doc above for why that broke chat signature validation. The
        // real (muffled) message is left completely alone and broadcasts
        // normally through vanilla's own ack-safe path.
        MinecraftServer server = sender.getServer();
        if (server == null) {
            return;
        }

        // ROUND (this session): [stated] wants the reveal line to appear
        // BELOW the real muffled message, not above it. The 1.3.34 attempt
        // deferred this by a single server.execute() and it wasn't enough -
        // [stated] confirmed the reveal is still appearing first. Most
        // likely explanation: real signed chat in 1.19.1+ is validated
        // asynchronously (off the main thread) and then hops BACK onto the
        // main thread via its own server.execute() call to actually
        // broadcast - meaning vanilla's real broadcast can itself be one
        // (or more) queued tasks behind where ServerChatEvent fires, not
        // something that finishes within the same task. A single defer
        // from here doesn't reliably land after that. Widened to a
        // double-defer (execute() scheduling another execute()) to clear
        // that same margin vanilla's own hop uses, rather than guessing a
        // fixed tick count.
        UUID senderId = sender.getUUID();
        Component revealLine = buildRevealLine(original);
        server.execute(() -> server.execute(() -> {
            HearUnmuffledSavedData data = HearUnmuffledSavedData.get(server);
            for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
                if (viewer.getUUID().equals(senderId)) {
                    continue;
                }
                if (data.isEnabled(viewer.getUUID())) {
                    viewer.sendSystemMessage(revealLine);
                }
            }
        }));
    }

    private static Component buildRevealLine(String original) {
        return Component.literal("    (" + original + ")")
                .withStyle(style -> style.withItalic(true).withColor(ChatFormatting.GRAY));
    }
}
