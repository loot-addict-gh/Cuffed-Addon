package com.example.cuffedaddon.client;

import com.example.cuffedaddon.CuffedAddon;
import com.example.cuffedaddon.init.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * Forces the vanilla damage-tilt (the screen lurch when you're hit) to play
 * while Electrization is active, even for a player who has turned it off in
 * Accessibility settings - per [stated]'s request after the first in-game test.
 *
 * <h2>Why this overrides the option instead of mixing into the renderer</h2>
 * The tilt is computed in {@code GameRenderer#bobHurt}, which multiplies its
 * whole rotation by {@code options.damageTiltStrength().get()}. A player with
 * that slider at 0 gets no tilt at all, so the only ways to force it are to
 * intercept that render method or to change what it reads.
 *
 * <p>Intercepting it would mean a Mixin on a VANILLA class, and this project
 * has no working Mixin annotation processor - {@code mixins.cuffedaddon.refmap.json}
 * is hand-maintained, so every vanilla-targeting mixin needs its SRG names
 * looked up and written in by hand. mappings.dev is not reachable from the
 * sandbox this was built in, which makes that route both fragile and slow.
 *
 * <p>Changing what it reads needs no mixin at all: {@code damageTiltStrength()}
 * is a public accessor returning {@code OptionInstance<Double>}, so the value is
 * swapped to 1.0 for the duration of the shock and put back afterwards.
 *
 * <h2>Why this can't strand the player's setting</h2>
 * Three things keep it safe:
 * <ul>
 *   <li>{@code OptionInstance#set} only touches memory. Options reach disk via
 *       {@code Options#save()}, which this never calls.</li>
 *   <li>The real value is restored the moment the effect ends - checked every
 *       client tick, not on an event that might not fire.</li>
 *   <li>It's also restored as soon as ANY screen opens. That's the one path
 *       where the override could otherwise be persisted: the options screen
 *       saves on close, so a player opening settings mid-shock could have
 *       written our 1.0 into their real config.</li>
 * </ul>
 *
 * <p>The tilt still only fires when the player is actually damaged
 * ({@code hurtTime > 0}), so this doesn't add a shake of its own - it just
 * stops the existing one from being suppressed.
 */
@Mod.EventBusSubscriber(modid = CuffedAddon.MODID, value = Dist.CLIENT)
public class ElectrizationClientEvents {

    /** Full-strength tilt, what vanilla's slider calls 100%. */
    private static final double FORCED_TILT_STRENGTH = 1.0D;

    /** The player's real setting, held only while overridden. Null = not overridden. */
    @Nullable
    private static Double savedTiltStrength = null;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        boolean shouldForce = player != null
                && minecraft.level != null
                && minecraft.screen == null
                && player.hasEffect(ModEffects.ELECTRIZATION.get());

        if (shouldForce) {
            applyOverride(minecraft);
        } else {
            restoreOverride(minecraft);
        }
    }

    private static void applyOverride(Minecraft minecraft) {
        if (savedTiltStrength != null) {
            return;
        }
        OptionInstance<Double> option = minecraft.options.damageTiltStrength();
        savedTiltStrength = option.get();
        option.set(FORCED_TILT_STRENGTH);
    }

    private static void restoreOverride(Minecraft minecraft) {
        if (savedTiltStrength == null) {
            return;
        }
        minecraft.options.damageTiltStrength().set(savedTiltStrength);
        savedTiltStrength = null;
    }
}
