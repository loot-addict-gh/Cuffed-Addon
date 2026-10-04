package com.example.cuffedaddon.enchantment;

import com.example.cuffedaddon.CuffedAddon;
import com.example.cuffedaddon.network.IllusionLegsSyncPacket;
import com.example.cuffedaddon.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps every client's idea of "whose legs are held still" in step with the
 * server.
 *
 * <p>This addon has a standing rule, learned the hard way when the Wall Restraint
 * pose turned out to be invisible to anyone who arrived late: <b>anything that
 * changes how a player looks to OTHERS needs three things - a broadcast when it
 * changes, a resend when a client starts tracking them, and a resend when they log
 * in.</b> All three are here. Drop any one and a player who walks into view later
 * sees the legs animating normally, which is exactly the tell Illusion exists to
 * avoid.
 *
 * <h2>Polling, rather than hooking equip and unequip</h2>
 * There is no Forge event for "a Cuffed restraint was equipped", and the routes in
 * are many - a key, a dispenser trap, Restraint Gaze, {@code /cuffed}, a release
 * keybind, death and respawn. Rather than find and hook every one of them and
 * still miss the next, the state is recomputed from what the player is wearing and
 * compared with what was last sent. It is one enchantment-tag scan every
 * {@link #INTERVAL_TICKS} ticks for a restrained player and nothing at all for
 * everyone else, which is far cheaper than the alternative is fragile.
 */
@Mod.EventBusSubscriber(modid = CuffedAddon.MODID)
public final class IllusionEvents {

    private static final int INTERVAL_TICKS = 5;

    /** What each player's clients were last told. Server-side only. */
    private static final Map<UUID, Boolean> LAST_SENT = new HashMap<>();

    private IllusionEvents() {
    }

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

        boolean illusion = IllusionUtil.hasIllusionLegsServerSide(player);
        Boolean lastSent = LAST_SENT.get(player.getUUID());
        if (lastSent != null && lastSent == illusion) {
            return;
        }
        LAST_SENT.put(player.getUUID(), illusion);
        broadcast(player, illusion);
    }

    /**
     * A client just started rendering this player - tell it, or it would draw the
     * legs walking until the next change, which for a stable disguise is forever.
     */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof ServerPlayer tracked)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer viewer)) {
            return;
        }
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer),
                new IllusionLegsSyncPacket(tracked.getUUID(),
                        IllusionUtil.hasIllusionLegsServerSide(tracked)));
    }

    /**
     * Logging in wearing one. Also clears the last-sent record, so the first tick
     * after a rejoin always sends rather than trusting a value from a previous
     * session on a client that has forgotten it.
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        boolean illusion = IllusionUtil.hasIllusionLegsServerSide(player);
        LAST_SENT.put(player.getUUID(), illusion);
        broadcast(player, illusion);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_SENT.remove(event.getEntity().getUUID());
    }

    private static void broadcast(ServerPlayer player, boolean illusion) {
        NetworkHandler.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new IllusionLegsSyncPacket(player.getUUID(), illusion));
    }
}
