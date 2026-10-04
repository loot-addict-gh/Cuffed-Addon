package com.example.cuffedaddon.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * The ONE plain Forge ModConfig.Type.SERVER config for this whole addon,
 * same category as Cuffed's own cuffed-server.toml: it holds balance values
 * (durability, whether it can be broken out of, whether it drops on break)
 * that need to be identical for every player in a world, so it lives in
 * <world>/serverconfig/ and gets synced to clients automatically. See
 * CuffedAddon#registerConfigs for where this gets registered as
 * "cuffedaddon-server.toml".
 *
 * STANDING RULE: every server-side config value for this addon belongs in
 * THIS class/file/toml - don't create a new config class or a new toml for
 * a new restraint/feature. Two earlier rounds got this wrong: first a
 * separate "cuffedaddon-straitjacket-server.toml"/StraitjacketConfig class
 * (reverted), then this shared class was still named "RopeConfig" (also
 * fixed, renamed to this) even though it held Anchoring/Straitjacket/every
 * other category too - which read as if everyone else's config was just
 * bolted onto Rope's own file. THIS class is the dedicated, neutrally-named
 * home for shared server config; every other class that needs a config
 * value reads it from here (e.g. `CuffedAddonServerConfig.ROPE_DURABILITY`),
 * it does not get its own config class. When adding a new
 * restraint/feature's config, append its new category at the BOTTOM of the
 * static initializer, after everything that already exists - don't insert
 * it in the middle.
 *
 * Durability shape matches Cuffed's own cuffed-server.toml convention
 * (compare its "Restraint Durabilities" category, which holds one shared
 * value per restraint TYPE - Handcuffs/Fuzzy Handcuffs/Shackles - rather
 * than a separate value per body part): a single "Restraint Durabilities"
 * category, positioned right below "Anchoring Settings" and above every
 * individual restraint category, holds one durability value per restraint
 * type here too (Rope, Straitjacket), used by both its arms and legs
 * restraint. The per-body-part categories below it only hold Can Be Broken
 * Out Of / Drop Item When Broken - no per-category Durability field.
 *
 * Durability values use plain `define(...)` (returns ConfigValue<Integer>),
 * NOT `defineInRange(...)` - defineInRange auto-appends a "#Range: ..."
 * comment line to the toml that [stated] explicitly doesn't want. Use plain
 * `define` for any future int config value in this file too, even if it
 * conceptually has a natural minimum - don't reach for defineInRange again.
 *
 * Note there's no "Rope when on Head"/"Straitjacket when on Head" category,
 * matching Duck Tape: both head variants are hardcoded to not be breakable
 * (see RopeHeadRestraint/StraitjacketHeadRestraint), so there's nothing to
 * configure for them.
 *
 * Also holds the "Anchoring Settings" category (per-block toggles for the
 * five new anchor points) - kept in this same file/toml on purpose, per-block
 * toggle text matched to Cuffed's own "Anchoring Settings" category style
 * (e.g. "Allow Anchoring To Fences"). Max Chain Length, Suffocation Length,
 * and Only-Restrained-Can-Be-Anchored are deliberately NOT duplicated here -
 * those stay read live from Cuffed's own config (via CuffedConfigBridge,
 * since Cuffed's config fields are typed with lazrslib's ConfigProperty
 * wrapper which isn't on this addon's compile classpath) so behavior is
 * identical regardless of which mod's config toggled a given anchor point on.
 *
 * Note on existing worlds/pre-existing toml files: Forge/NightConfig
 * preserves the on-disk ORDER of categories/keys it already recognizes when
 * loading an existing toml, and only appends genuinely new keys - it does
 * NOT re-sort an existing file to match this class's declaration order. A
 * world whose cuffedaddon-server.toml predates a given reordering here will
 * keep showing categories in whatever order they were in before; only a
 * freshly generated toml (new world, or the file deleted) will lay out in
 * the order declared below.
 */
public class CuffedAddonServerConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ANCHORING_ALLOW_ANCHORING_TO_IRON_BARS;
    public static final ForgeConfigSpec.BooleanValue ANCHORING_ALLOW_ANCHORING_TO_REINFORCED_BARS;
    public static final ForgeConfigSpec.BooleanValue ANCHORING_ALLOW_ANCHORING_TO_REINFORCED_BARS_WINDOWS;
    public static final ForgeConfigSpec.BooleanValue ANCHORING_ALLOW_ANCHORING_TO_CHAIN;
    public static final ForgeConfigSpec.BooleanValue ANCHORING_ALLOW_ANCHORING_TO_LIGHTNING_ROD;
    public static final ForgeConfigSpec.BooleanValue ANCHORING_ALLOW_ANCHORING_TO_END_ROD;

    public static final ForgeConfigSpec.ConfigValue<Integer> ROPE_DURABILITY;
    public static final ForgeConfigSpec.ConfigValue<Integer> STRAITJACKET_DURABILITY;
    public static final ForgeConfigSpec.ConfigValue<Integer> SHOCK_COLLAR_DURABILITY;

    public static final ForgeConfigSpec.BooleanValue SHOCK_COLLAR_CAN_BE_BROKEN_OUT_OF;
    public static final ForgeConfigSpec.BooleanValue SHOCK_COLLAR_DROP_ITEM_WHEN_BROKEN;
    public static final ForgeConfigSpec.ConfigValue<Integer> ELECTRIZATION_SLOWNESS_AMPLIFIER;
    public static final ForgeConfigSpec.ConfigValue<Integer> ELECTRIZATION_WEAKNESS_AMPLIFIER;
    public static final ForgeConfigSpec.ConfigValue<Integer> ARROW_ELECTRIZATION_SECONDS;

    public static final ForgeConfigSpec.BooleanValue ROPE_ON_ARMS_CAN_BE_BROKEN_OUT_OF;
    public static final ForgeConfigSpec.BooleanValue ROPE_ON_ARMS_DROP_ITEM_WHEN_BROKEN;

    public static final ForgeConfigSpec.BooleanValue ROPE_ON_LEGS_CAN_BE_BROKEN_OUT_OF;
    public static final ForgeConfigSpec.BooleanValue ROPE_ON_LEGS_DROP_ITEM_WHEN_BROKEN;

    public static final ForgeConfigSpec.BooleanValue STRAITJACKET_ON_ARMS_CAN_BE_BROKEN_OUT_OF;
    public static final ForgeConfigSpec.BooleanValue STRAITJACKET_ON_ARMS_DROP_ITEM_WHEN_BROKEN;

    public static final ForgeConfigSpec.BooleanValue STRAITJACKET_ON_LEGS_CAN_BE_BROKEN_OUT_OF;
    public static final ForgeConfigSpec.BooleanValue STRAITJACKET_ON_LEGS_DROP_ITEM_WHEN_BROKEN;

    public static final ForgeConfigSpec.BooleanValue PLAYER_PICKER_AUTO_RELEASE_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<Integer> PLAYER_PICKER_AUTO_RELEASE_SECONDS;

    // Restraint Traps (1.5.13).
    public static final ForgeConfigSpec.BooleanValue TRAP_DISPENSER_RESTRAIN_ENABLED;
    public static final ForgeConfigSpec.BooleanValue TRAP_DISPENSER_KEY_ENABLED;
    public static final ForgeConfigSpec.BooleanValue TRAP_DISPENSER_BED_ENABLED;
    public static final ForgeConfigSpec.BooleanValue TRAP_REDSTONE_ENABLED;

    public static final ForgeConfigSpec.BooleanValue ENCHANTMENTS_AFFECT_FAKE_PLAYERS;
    public static final ForgeConfigSpec.BooleanValue GAZE_CHAIN;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("Anchoring Settings");
        ANCHORING_ALLOW_ANCHORING_TO_IRON_BARS = builder
                .comment("Whether or not players should be allowed to anchor entities to IRON BARS.")
                .define("Allow Anchoring To Iron Bars", true);
        ANCHORING_ALLOW_ANCHORING_TO_REINFORCED_BARS = builder
                .comment("Whether or not players should be allowed to anchor entities to REINFORCED BARS.")
                .define("Allow Anchoring To Reinforced Bars", true);
        ANCHORING_ALLOW_ANCHORING_TO_REINFORCED_BARS_WINDOWS = builder
                .comment("Whether or not players should be allowed to anchor entities to REINFORCED BARS WINDOWS.")
                .define("Allow Anchoring To Reinforced Bars Windows", true);
        ANCHORING_ALLOW_ANCHORING_TO_CHAIN = builder
                .comment("Whether or not players should be allowed to anchor entities to CHAIN.")
                .define("Allow Anchoring To Chain", true);
        ANCHORING_ALLOW_ANCHORING_TO_LIGHTNING_ROD = builder
                .comment("Whether or not players should be allowed to anchor entities to LIGHTNING ROD.")
                .define("Allow Anchoring To Lightning Rod", true);
        ANCHORING_ALLOW_ANCHORING_TO_END_ROD = builder
                .comment("Whether or not players should be allowed to anchor entities to END ROD.")
                .define("Allow Anchoring To End Rod", true);
        builder.pop();

        builder.push("Restraint Durabilities");
        ROPE_DURABILITY = builder
                .comment("The amount of durability rope has.")
                .define("Rope Durability", 10);
        STRAITJACKET_DURABILITY = builder
                .comment("The amount of durability a straitjacket has.")
                .define("Straitjacket Durability", 50);
        SHOCK_COLLAR_DURABILITY = builder
                .comment("The amount of durability a shock collar has.")
                .define("Shock Collar Durability", 50);
        builder.pop();

        builder.push("Rope when on Arms");
        ROPE_ON_ARMS_CAN_BE_BROKEN_OUT_OF = builder
                .comment("Whether or not this restraint can be broken out of.")
                .define("Can Be Broken Out Of", true);
        ROPE_ON_ARMS_DROP_ITEM_WHEN_BROKEN = builder
                .comment("Whether or not to drop the item when broken out of.")
                .define("Drop Item When Broken", true);
        builder.pop();

        builder.push("Rope when on Legs");
        ROPE_ON_LEGS_CAN_BE_BROKEN_OUT_OF = builder
                .comment("Whether or not this restraint can be broken out of.")
                .define("Can Be Broken Out Of", true);
        ROPE_ON_LEGS_DROP_ITEM_WHEN_BROKEN = builder
                .comment("Whether or not to drop the item when broken out of.")
                .define("Drop Item When Broken", true);
        builder.pop();

        builder.push("Straitjacket when on Arms");
        STRAITJACKET_ON_ARMS_CAN_BE_BROKEN_OUT_OF = builder
                .comment("Whether or not this restraint can be broken out of.")
                .define("Can Be Broken Out Of", true);
        STRAITJACKET_ON_ARMS_DROP_ITEM_WHEN_BROKEN = builder
                .comment("Whether or not to drop the item when broken out of.")
                .define("Drop Item When Broken", true);
        builder.pop();

        builder.push("Straitjacket when on Legs");
        STRAITJACKET_ON_LEGS_CAN_BE_BROKEN_OUT_OF = builder
                .comment("Whether or not this restraint can be broken out of.")
                .define("Can Be Broken Out Of", true);
        STRAITJACKET_ON_LEGS_DROP_ITEM_WHEN_BROKEN = builder
                .comment("Whether or not to drop the item when broken out of.")
                .define("Drop Item When Broken", true);
        builder.pop();

        // Shock Collar (1.4.32). Appended at the bottom, per the standing rule
        // in this class's doc comment. Note there is deliberately no "Drop Item
        // When Broken" here, unlike Rope/Straitjacket: [stated]'s spec is that
        // breaking out destroys BOTH the collar and its bound remote, so there
        // is nothing left to drop.
        builder.push("Shock Collar");
        SHOCK_COLLAR_CAN_BE_BROKEN_OUT_OF = builder
                .comment("Whether or not this restraint can be broken out of.")
                .define("Can Be Broken Out Of", true);
        SHOCK_COLLAR_DROP_ITEM_WHEN_BROKEN = builder
                .comment("Whether or not to drop the item when broken out of.")
                // 1.5.23: default flipped false -> true at [stated]'s request.
                // Salvage is the kinder default - the collar comes apart and the
                // bound remote reverts to a plain Shock Remote, instead of both
                // halves being destroyed outright. Existing worlds keep whatever
                // is already written in their cuffedaddon-server.toml; this only
                // changes what a FRESH config file is generated with.
                .define("Drop Item When Broken", true);
        builder.pop();

        builder.push("Electrization");
        ELECTRIZATION_SLOWNESS_AMPLIFIER = builder
                .comment("Amplifier of the Slowness applied while being shocked.")
                .define("Slowness Amplifier", 0);
        ELECTRIZATION_WEAKNESS_AMPLIFIER = builder
                .comment("Amplifier of the Weakness applied while being shocked.")
                .define("Weakness Amplifier", 0);
        builder.pop();

        // Player Picker (1.4.40). Appended at the bottom, per the standing rule
        // in this class's doc comment. Off by default at [stated]'s explicit
        // request: /unpick is the primary escape hatch and this is only the
        // unattended backstop, so it stays opt-in.
        builder.push("Player Picker");
        PLAYER_PICKER_AUTO_RELEASE_ENABLED = builder
                .comment("Whether a picked player is freed automatically if their item is destroyed.")
                .define("Auto Release When Item Lost", false);
        PLAYER_PICKER_AUTO_RELEASE_SECONDS = builder
                .comment("Seconds the item must stay missing before that happens.")
                .define("Auto Release Delay Seconds", 30);
        builder.pop();

        // Restraint Traps (1.5.13). Appended at the bottom, per the standing
        // rule in this class's doc comment. All default ON: unlike the Player
        // Picker's auto-release, none of these can act on a player who has not
        // walked into the trap, and a dispenser that refuses to restrain simply
        // dispenses the item as normal.
        builder.push("Restraint Traps");
        TRAP_DISPENSER_RESTRAIN_ENABLED = builder
                .comment("Whether dispensers can put restraints on a player in front of them.")
                .define("Dispensers Can Restrain", true);
        TRAP_DISPENSER_KEY_ENABLED = builder
                .comment("Whether a dispensed key unlocks the arm restraints of a player in front.")
                .define("Dispensers Can Unlock Arms", true);
        TRAP_DISPENSER_BED_ENABLED = builder
                .comment("Whether dispensers can apply the Bed Restraint to a player on a bed.")
                .define("Dispensers Can Bed Restrain", true);
        TRAP_REDSTONE_ENABLED = builder
                .comment("Whether a redstone signal makes pillories and Wall Restraints catch players.")
                .define("Redstone Traps", true);
        builder.pop();

        // Restraint Enchantments (1.5.15). Appended at the bottom, per the
        // standing rule in this class's doc comment.
        //
        // The fake players toggle covers REPELLENT ONLY, and is ON by default as
        // of 1.5.17. It briefly covered Restraint Gaze too, at 1.5.15; [stated]
        // removed that - "restraint gaze should never be in effect for fake
        // players" - so RestraintGazeEvents does not read this value at all any
        // more, and its candidate query is typed to real players so it cannot.
        //
        // The chain toggle is ON by default, which is the viral behaviour [stated]
        // chose: a gazed-on restraint keeps Restraint Gaze and can gaze at someone
        // else in turn. Turn it off and the copy keeps every OTHER enchantment it
        // had (Repellent, Famine, and so on) but loses Gaze, so a chain stops at
        // one hop.
        //
        // Repellent's push strength is deliberately NOT here - see RepellentEvents
        // for where it lives and why it is a balance constant rather than config.
        builder.push("Restraint Enchantments");
        ENCHANTMENTS_AFFECT_FAKE_PLAYERS = builder
                .comment("Whether Repellent works on Fake Players.")
                .define("Works On Fake Players", true);
        GAZE_CHAIN = builder
                .comment("Whether a restraint applied by Restraint Gaze copies the enchantment")
                .define("Restraint Gaze Chain", true);
        builder.pop();

        // Arrow of Electrization (1.6.1). Appended at the bottom, per the
        // standing rule in this class's doc comment, rather than folded into the
        // Electrization category above - that one is the Shock Collar's, and the
        // arrow is a separate delivery mechanism whose duration [stated] set
        // independently (5 seconds, against the collar's 2 per press).
        builder.push("Arrow of Electrization");
        ARROW_ELECTRIZATION_SECONDS = builder
                .comment("Seconds of Electrization applied by an Arrow of Electrization.")
                .define("Electrization Seconds", 5);
        builder.pop();

        SPEC = builder.build();
    }
}
