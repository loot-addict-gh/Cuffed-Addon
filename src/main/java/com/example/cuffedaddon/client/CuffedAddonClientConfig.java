package com.example.cuffedaddon.client;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * A plain Forge ModConfig.Type.CLIENT config - unlike CuffedAddonServerConfig (SERVER,
 * synced to every player so gameplay balance stays identical world-wide),
 * this holds a purely cosmetic, purely local preference: whether THIS
 * player's own client shows Cuffed's original center-screen restraint HUD,
 * or this addon's compact bottom-left version. Lives in the player's own
 * config/ folder as "cuffedaddon-client.toml", never touches the server or
 * any other player - per [stated]'s explicit choice (per-player command,
 * no server config, no networking).
 *
 * The /originalGui command (OriginalGuiClientCommand) is the normal way to
 * change this, but since it's a real config file it can also be hand-edited
 * or left alone for persistence across restarts either way.
 */
public class CuffedAddonClientConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue SHOW_ORIGINAL_GUI;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("Restraint HUD");
        SHOW_ORIGINAL_GUI = builder
                .comment(
                        "false (default): use this addon's compact restraint HUD - small",
                        "head/arms/legs icons stacked in the bottom-left corner, no center-",
                        "screen text.",
                        "true: use Cuffed's own original restraint HUD (icons + text",
                        "centered on screen), exactly as if this addon's HUD change didn't",
                        "exist.",
                        "Can also be changed in-game with /originalGui <true|false>.")
                .define("Show Original Cuffed GUI", false);
        builder.pop();

        SPEC = builder.build();
    }
}
