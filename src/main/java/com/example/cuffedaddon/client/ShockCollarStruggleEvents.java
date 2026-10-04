package com.example.cuffedaddon.client;

import com.example.cuffedaddon.CuffedAddon;
import com.example.cuffedaddon.network.NetworkHandler;
import com.example.cuffedaddon.network.ShockCollarStrugglePacket;
import com.lazrproductions.cuffed.api.CuffedAPI;
import com.lazrproductions.cuffed.cap.base.IRestrainableCapability;
import com.lazrproductions.cuffed.restraints.base.AbstractArmRestraint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

/**
 * The wearer's side of struggling out of a Shock Collar.
 *
 * <h2>The input channel</h2>
 * [stated]'s design: spam mouse clicks <i>while holding one of Cuffed's cutlery
 * items</i> (fork, spoon or knife). Two things that buys, both deliberate:
 *
 * <ul>
 *   <li><b>No channel collision with arm restraints.</b> Cuffed's breakable ARM
 *       restraints already consume raw mouse clicks. Requiring cutlery in hand
 *       means a click is only ever working on one of the two.</li>
 *   <li><b>You can't reach your own neck with bound arms.</b> Enforced via
 *       {@code AllowItemUse()} rather than "is any arm restraint present" -
 *       [stated] chose this case explicitly, so Shackles (the one arm restraint
 *       that permits item use) leave you able to work the collar, while
 *       Handcuffs, Fuzzy Handcuffs, Duck Tape, Rope and Straitjacket do not.</li>
 * </ul>
 *
 * There's also precedent for cutlery as an escape tool in Cuffed itself:
 * {@code ModServerEvents} lets a fork or spoon chip away at reinforced blocks,
 * 25% per hit, costing a durability point each time.
 *
 * <h2>Feel</h2>
 * Deliberately mirrors Cuffed's own breakable-restraint feel rather than
 * inventing one: 50% chance per attempt, a randomised cooldown afterwards, and
 * ALTERNATING mouse buttons required, so holding or mashing a single button
 * achieves nothing. The audio cue is handcuffs' exactly, per [stated] -
 * {@code SoundEvents.CHAIN_STEP} at pitch 0.9-1.1 (verified against both
 * Cuffed's GitHub source and a CFR decompile of the shipped 1.3.15 jar).
 *
 * <p>Nothing here is authoritative. Every attempt is re-validated server-side;
 * this class only decides when to bother the server and when to play a sound.
 */
@Mod.EventBusSubscriber(modid = CuffedAddon.MODID, value = Dist.CLIENT)
public class ShockCollarStruggleEvents {

    private static final double BREAK_CHANCE = 0.5;
    private static final Random RANDOM = new Random();

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ClientCollaredState.tickCooldown();
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        if (!ClientCollaredState.isCollared()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        // Any open screen (inventory, chat, a Possessions Box) means these
        // clicks belong to the GUI, not to struggling.
        if (minecraft.screen != null || minecraft.player == null || minecraft.level == null) {
            return;
        }

        int button = event.getButton();
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            return;
        }

        LocalPlayer player = minecraft.player;
        if (!isHoldingCutlery(player)) {
            return;
        }
        if (!armsAllowItemUse(player)) {
            return;
        }
        if (!ClientCollaredState.isCooldownOver()) {
            return;
        }
        if (button == ClientCollaredState.getLastButton()) {
            // Alternation requirement - same rule Cuffed's own breakable
            // restraints use via requireAlternateKeysToAttemptBreak().
            return;
        }

        if (RANDOM.nextDouble() >= BREAK_CHANCE) {
            return;
        }

        ClientCollaredState.setLastButton(button);
        ClientCollaredState.setCooldown(RANDOM.nextInt(20) + 20.0f);
        player.playNotifySound(SoundEvents.CHAIN_STEP, SoundSource.PLAYERS, 1.0f,
                Mth.randomBetween(player.getRandom(), 0.9f, 1.1f));
        NetworkHandler.CHANNEL.sendToServer(new ShockCollarStrugglePacket());
    }

    private static boolean isHoldingCutlery(LocalPlayer player) {
        return isCutlery(player.getMainHandItem()) || isCutlery(player.getOffhandItem());
    }

    private static boolean isCutlery(ItemStack stack) {
        return stack.is(com.lazrproductions.cuffed.init.ModItems.FORK.get())
                || stack.is(com.lazrproductions.cuffed.init.ModItems.SPOON.get())
                || stack.is(com.lazrproductions.cuffed.init.ModItems.KNIFE.get());
    }

    private static boolean armsAllowItemUse(LocalPlayer player) {
        IRestrainableCapability restrainable = CuffedAPI.Capabilities.getRestrainableCapability(player);
        if (restrainable == null) {
            return true;
        }
        AbstractArmRestraint arms = restrainable.getArmRestraint();
        return arms == null || arms.AllowItemUse();
    }
}
