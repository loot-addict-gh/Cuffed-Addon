package com.example.cuffedaddon.client;

import com.example.cuffedaddon.CuffedAddon;
import com.example.cuffedaddon.network.NetworkHandler;
import com.example.cuffedaddon.network.ReleaseRestraintPacket;
import com.lazrproductions.cuffed.restraints.base.RestraintType;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * [stated]'s own explicit spec: 3 new keybinds under a new "Cuffed Addon"
 * category ("Release Arms"/"Release Head"/"Release Legs"), all unbound by
 * default, that release the PRESSING PLAYER's OWN restraint in that slot -
 * confirmed via AskUserQuestion that this is a self-release shortcut
 * (useful since a restrained player normally can't remove their own
 * restraints - most likely wanted for solo testing), NOT a
 * release-whoever-you're-looking-at tool.
 *
 * [stated] explicitly asked for these to "use" Cuffed's own real
 * `/cuffed <player> remove <Arm|Leg|Head>` command (confirmed exact syntax
 * by cloning Cuffed's real source, command/HandcuffCommand.java) rather
 * than reimplementing its effect by calling the underlying capability
 * method directly - see ReleaseRestraintPacket's own doc for how the
 * server side actually dispatches that real command text despite it
 * normally requiring permission level 3 (a plain player has no reason to
 * be OP just to use their own self-release keybind).
 *
 * KeyMapping FIELDS + the per-tick consumeClick() polling both live here,
 * on the regular Forge event bus (this class's default bus, same as
 * ClientWallPoseEvents/ClientEvents) - REGISTERING them with the game is a
 * separate mod-bus concern (RegisterKeyMappingsEvent), handled by
 * ClientModEvents' own mod-bus listener referencing these same fields, so
 * that class doesn't need to change its bus.
 */
@Mod.EventBusSubscriber(modid = CuffedAddon.MODID, value = Dist.CLIENT)
public class CuffedAddonKeybinds {

    private static final String CATEGORY = "key.categories.cuffedaddon";

    public static final KeyMapping RELEASE_ARMS = new KeyMapping(
            "key.cuffedaddon.release_arms", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping RELEASE_HEAD = new KeyMapping(
            "key.cuffedaddon.release_head", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping RELEASE_LEGS = new KeyMapping(
            "key.cuffedaddon.release_legs", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (Minecraft.getInstance().player == null) return;

        // NEW THIS ROUND: [stated] confirmed the server-side-only crouch
        // fix in PlayerPickerUtil#tickPickedPlayer (isShiftKeyDown reset +
        // forced Pose.STANDING) still didn't stop them crouching while
        // picked. That's because the crouch camera/eye-height blend is
        // CLIENT-PREDICTED straight from Minecraft.options.keyShift's own
        // held state - entirely local, unaffected by anything the server
        // resets. Forcibly clearing that KeyMapping every client tick
        // while ClientPickedState says we're picked (synced by
        // PlayerPickedSelfSyncPacket on pick/release) stops the local
        // crouch prediction from ever triggering in the first place,
        // rather than reactively fighting its visible effects.
        if (ClientPickedState.isPicked()) {
            // CORRECTED (was keySneak - real compiler error [stated] pasted:
            // "cannot find symbol: variable keySneak, location: variable
            // options of type Options"). The real field name on vanilla's
            // Options class is keyShift (confirmed against Forge's own
            // 1.20.1 javadocs) - "shift" being the default sneak key,
            // vanilla just never renamed the field when sneak became
            // independently rebindable.
            Minecraft.getInstance().options.keyShift.setDown(false);
        }

        // while(), not if() - consumeClick() only returns true once per
        // actual press even if multiple presses queued up in one tick;
        // draining the queue this way is the standard KeyMapping pattern
        // (matches vanilla's own key-handling loops).
        while (RELEASE_ARMS.consumeClick()) {
            NetworkHandler.CHANNEL.sendToServer(new ReleaseRestraintPacket(RestraintType.Arm));
        }
        while (RELEASE_HEAD.consumeClick()) {
            NetworkHandler.CHANNEL.sendToServer(new ReleaseRestraintPacket(RestraintType.Head));
        }
        while (RELEASE_LEGS.consumeClick()) {
            NetworkHandler.CHANNEL.sendToServer(new ReleaseRestraintPacket(RestraintType.Leg));
        }
    }
}
