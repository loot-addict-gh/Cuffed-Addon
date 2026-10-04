package com.example.cuffedaddon;

import com.example.cuffedaddon.collar.ShockCollarUtil;
import com.example.cuffedaddon.picker.PlayerPickerUtil;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CuffedAddon.MODID)
public class ModCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        registerRemoveCollar(dispatcher);
        registerHearUnmuffled(dispatcher);
        registerUnpick(dispatcher);
    }

    /**
     * {@code /unpick <target>} - frees a player captured by a Player Picker.
     *
     * <p><b>Why this is a top-level command</b>, when this project's standing
     * rule is that the addon adds none. [stated] set that rule aside explicitly
     * for this one: <i>"I am willing to take a step back on 'no custom top level
     * commands' if it means preventing such issues"</i>. The alternative offered
     * was grafting it onto Cuffed's own tree as {@code /cuffed <player> unpick},
     * the way {@code remove Collar} is grafted (see
     * {@link #registerRemoveCollar}), and that was turned down: <i>"adding
     * another entry to /cuffed &lt;player&gt; remove would make it too
     * cluttered."</i> So this is a deliberate, narrow exception, not the rule
     * lapsing - anything else still goes on an existing command or a gamerule.
     *
     * <p><b>What it fixes.</b> A capture's only handle is its item, so destroying
     * the item stranded the captured player in spectator permanently - [stated]
     * hit this for real and their friend could not be freed by any means:
     * {@code /gamemode} is re-asserted back to spectator every tick for as long
     * as the capability says they are picked, and removing their restraints does
     * not touch the capture either. Only clearing that capability frees them, and
     * before this round nothing but the item could do it.
     *
     * <p>Releases whether or not the item still exists, then empties the item if
     * it can be found - see {@code PlayerPickerUtil#tryUnpick}. The target must
     * be online: a capture is only ever actively holding someone while they are
     * (and {@code EntityArgument.player()} resolves online players only), but
     * that is not a real limitation here, since a stranded player has to log in
     * to discover they are stranded.
     *
     * <p><b>Permission level 3, chosen deliberately by [stated]</b> after the
     * trade-off was explained: <i>"I dont want /unpick to be runnable by command
     * blocks: I want it to be a decision made by an admin"</i>. Level 3 is the
     * admin tier ({@code /ban}, {@code /kick}, {@code /op}), and crucially it is
     * ABOVE the level 2 that command blocks and datapack functions run at, so
     * freeing a capture cannot be automated or wired to redstone - it takes a
     * real operator. This also lines it up with Cuffed's own {@code /cuffed}
     * tree, which is at 3.
     *
     * <p>Note the deliberate absence of a {@code || !source.isPlayer()} escape
     * of the kind Cuffed's own command carries. That clause exists to let the
     * console and command blocks through regardless of level, which is exactly
     * what is NOT wanted here. The server console is level 4 and so passes the
     * check on its own merits anyway; command blocks, at 2, do not.
     *
     * <p>The addon's gamerule additions stay at 2, per [stated] in the same
     * message - matching vanilla's own {@code /gamerule}.
     */
    private static void registerUnpick(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("unpick")
                        .requires(source -> source.hasPermission(3))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ModCommands::executeUnpick)));
    }

    private static int executeUnpick(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        MinecraftServer server = ctx.getSource().getServer();

        if (PlayerPickerUtil.tryUnpick(server, target)) {
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    "command.cuffedaddon.unpick.success", target.getName()), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.translatable(
                "command.cuffedaddon.unpick.failure", target.getName()));
        return 0;
    }

    /**
     * Adds {@code Collar} to Cuffed's OWN {@code /cuffed <player> remove ...},
     * alongside its Arm / Leg / Head - per [stated], who wanted a command escape
     * for a collar whose remote is gone, and wanted it on the existing command
     * rather than a new top-level one.
     *
     * <p><b>No mixin needed.</b> Brigadier's {@code CommandNode#addChild} merges
     * by node NAME rather than replacing, so re-registering a path that already
     * exists just grafts new children onto the existing nodes. The node names
     * below therefore have to match Cuffed's exactly - the {@code "player"}
     * argument especially, since a differently-named argument would sit beside
     * Cuffed's instead of merging into it. This is the same technique
     * {@link #registerHearUnmuffled} uses against vanilla's /gamerule, and
     * Cuffed itself relies on it: HandcuffCommand and CuffedDebugCommand each
     * register their own {@code literal("cuffed")} and are merged together.
     *
     * <p><b>Collar is a LITERAL, deliberately.</b> Cuffed's {@code remove} takes
     * an {@code EnumArgument} over its own {@code RestraintType} (Arm, Leg,
     * Head) - an enum this addon cannot extend. A literal sibling of that
     * argument node works because Brigadier tries literals before arguments, so
     * {@code Collar} matches here and {@code Arm} still falls through to
     * Cuffed's enum. Both show up in suggestions.
     *
     * <p>The {@code requires} mirrors Cuffed's exactly. That matters for the
     * case where this registers FIRST: the merge copies children but NOT the
     * requirement, so Cuffed merging into our node would otherwise drop its
     * permission check and leave all of {@code /cuffed} unrestricted.
     */
    private static void registerRemoveCollar(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("cuffed")
                        .requires(source -> source.hasPermission(3) || !source.isPlayer())
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.literal("remove")
                                        .then(Commands.literal("Collar")
                                                .executes(ModCommands::executeRemoveCollar)))));
    }

    private static int executeRemoveCollar(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        // getPlayer() is null for console / command blocks; forceRemoveCollar
        // drops the returned collar at the target in that case rather than
        // letting it vanish.
        boolean removed = ShockCollarUtil.forceRemoveCollar(target, ctx.getSource().getPlayer());

        if (removed) {
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    "command.cuffedaddon.remove_collar.success", target.getName()), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.translatable(
                "command.cuffedaddon.remove_collar.failure", target.getName()));
        return 0;
    }

    private static void registerHearUnmuffled(CommandDispatcher<CommandSourceStack> dispatcher) {
        // "hearUnmuffled" hangs off /gamerule as a literal child rather than a real
        // GameRules.Key, since it's per-player (not one global value) and can't be a
        // normal GameRuleCommand-generated boolean rule.
        //
        // Brigadier's CommandNode#addChild merges children by name when you register
        // a literal that already exists in the dispatcher, instead of replacing it --
        // so re-registering literal("gamerule") here just adds "hearUnmuffled" as a
        // sibling of vanilla's own gamerule children (doDaylightCycle, keepInventory,
        // ...). This relies on vanilla's /gamerule node already being registered by
        // the time RegisterCommandsEvent fires, which it is: Forge fires that event
        // from inside the Commands constructor, after all of vanilla's own
        // xxxCommand.register(dispatcher) calls have already run.
        dispatcher.register(
                Commands.literal("gamerule")
                        .then(Commands.literal("hearUnmuffled")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("target", EntityArgument.player())
                                        // /gamerule hearUnmuffled <target> <true|false>
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(ctx -> {
                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                                    boolean value = BoolArgumentType.getBool(ctx, "value");
                                                    MinecraftServer server = ctx.getSource().getServer();
                                                    HearUnmuffledSavedData.get(server).set(target.getUUID(), value);

                                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                                            "Gamerule hearUnmuffled is now "
                                                                    + value + " for " + target.getName().getString()
                                                    ), true);
                                                    return 1;
                                                })
                                        )
                                        // /gamerule hearUnmuffled <target>  -- query current value
                                        .executes(ctx -> {
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                            MinecraftServer server = ctx.getSource().getServer();
                                            boolean value = HearUnmuffledSavedData.get(server).isEnabled(target.getUUID());

                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    "Gamerule hearUnmuffled is currently set to: " + value
                                                            + " for " + target.getName().getString()
                                            ), false);
                                            return 1;
                                        })
                                )
                        )
        );
    }
}
