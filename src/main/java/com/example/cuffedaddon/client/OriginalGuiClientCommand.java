package com.example.cuffedaddon.client;

import com.example.cuffedaddon.CuffedAddon;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * /originalGui <true|false> - purely local, client-side command (registered
 * via RegisterClientCommandsEvent, not the normal server RegisterCommandsEvent
 * ModCommands uses) that flips CuffedAddonClientConfig.SHOW_ORIGINAL_GUI.
 * Works the same in singleplayer or on any multiplayer server, with no
 * permission requirement and no server involvement at all - it only changes
 * how the issuing player's OWN client renders its OWN restraint HUD.
 *
 * NOTE: no bus = Bus.MOD here, unlike ClientModEvents/CurioFriskingClientEvents
 * - those listen for genuine mod-bus lifecycle events (FMLClientSetupEvent,
 * EntityRenderersEvent, both IModBusEvent). RegisterClientCommandsEvent is a
 * plain Forge-bus event (like PlayerInteractEvent or RenderGuiOverlayEvent),
 * same as ClientLiePoseEvents' pattern - the default (unspecified) bus here IS
 * Bus.FORGE. Mismatching this throws exactly the IllegalArgumentException
 * [stated] hit: "...has @SubscribeEvent annotation, but takes an argument
 * that is not a subtype of...IModBusEvent".
 */
@Mod.EventBusSubscriber(modid = CuffedAddon.MODID, value = Dist.CLIENT)
public class OriginalGuiClientCommand {

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("originalGui")
                        // /originalGui <true|false>  -- set it
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    boolean value = BoolArgumentType.getBool(ctx, "value");
                                    CuffedAddonClientConfig.SHOW_ORIGINAL_GUI.set(value);
                                    CuffedAddonClientConfig.SHOW_ORIGINAL_GUI.save();

                                    ctx.getSource().sendSuccess(() -> Component.literal(value
                                            ? "Showing Cuffed's original restraint HUD."
                                            : "Showing the compact restraint HUD."), false);
                                    return 1;
                                })
                        )
                        // /originalGui  -- query current value
                        .executes(ctx -> {
                            boolean value = CuffedAddonClientConfig.SHOW_ORIGINAL_GUI.get();
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Original Cuffed restraint HUD is currently: " + value), false);
                            return 1;
                        })
        );
    }
}
