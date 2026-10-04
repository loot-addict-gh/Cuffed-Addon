package com.example.cuffedaddon.client;

import com.example.cuffedaddon.CuffedAddon;
import com.example.cuffedaddon.enchantment.IllusionUtil;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The two pieces of Illusion client bookkeeping.
 *
 * <h2>Clearing the set on disconnect</h2>
 * The "whose legs are held still" set is keyed by UUID and never expires on its
 * own, so without this a player who was Illusion-restrained on one server would
 * still be remembered as such after disconnecting and joining another - and would
 * render with locked legs there until something corrected it. Cheap to prevent,
 * confusing to debug.
 *
 * <h2>Mirroring the local player's own state (1.5.20)</h2>
 * Sprinting is decided in {@code LocalPlayer#canStartSprinting}, which asks a
 * throwaway restraint instance that knows neither its enchantments nor its wearer
 * - see {@code IllusionUtil#localPlayerHasIllusionLegs()} for why that means the
 * answer has to be ambient. This keeps that flag in step with the synced set.
 *
 * <p>A tick handler rather than the sync packet, on purpose: the packet arrives
 * keyed by UUID and can land before {@code Minecraft#player} exists (the login
 * resend races world join), whereas re-reading it every tick cannot get out of
 * step with the set no matter what order things arrive in. One set lookup a tick.
 */
@Mod.EventBusSubscriber(modid = CuffedAddon.MODID, value = Dist.CLIENT)
public final class IllusionClientEvents {

    private IllusionClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        IllusionUtil.setLocalIllusionLegs(
                IllusionUtil.hasIllusionLegsClientSide(Minecraft.getInstance().player));
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        IllusionUtil.clearClientState();
    }
}
