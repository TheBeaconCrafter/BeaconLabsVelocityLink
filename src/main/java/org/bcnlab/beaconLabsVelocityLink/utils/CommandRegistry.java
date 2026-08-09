package org.bcnlab.beaconLabsVelocityLink.utils;

import org.bcnlab.beaconLabsVelocityLink.BeaconLabsVelocityLink;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import java.util.List;
import java.util.function.BiConsumer;
import org.bukkit.command.CommandExecutor;
import org.bcnlab.beaconLabsVelocityLink.commands.LabsLinkCommand;

public class CommandRegistry {

    public static void registerAll(BeaconLabsVelocityLink plugin, Commands commands) {
        registerPlayerCommand(commands, plugin, "nick", "Change your nickname", List.of("nickname"), (p, args) -> plugin.handleNick(p, args));
        registerPlayerCommand(commands, plugin, "unnick", "Remove your nickname", List.of(), (p, args) -> plugin.handleNickRemove(p, args));
        registerPlayerCommand(commands, plugin, "pvanish", "Hide yourself from other players", List.of("pv"), (p, args) -> plugin.handleVanish(p, args));
        registerPlayerCommand(commands, plugin, "settings", "Open your settings", List.of(), plugin::handleSettings);
        registerPlayerCommand(commands, plugin, "friends", "Open your friends list", List.of("friend", "f"), plugin::handleFriends);
        registerLegacyCommand(commands, "labslink", "Prints plugin information", List.of(), new LabsLinkCommand(plugin));
    }

    private static void registerPlayerCommand(Commands commands, BeaconLabsVelocityLink plugin, String name, String description, List<String> aliases, BiConsumer<Player, String[]> action) {
        LiteralCommandNode<CommandSourceStack> node = Commands.literal(name)
            .executes(ctx -> {
                CommandSender sender = ctx.getSource().getSender();
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(plugin.getPrefix(sender).append(Component.text("Only players can use this command.", NamedTextColor.RED)));
                    return Command.SINGLE_SUCCESS;
                }
                action.accept(player, new String[0]);
                return Command.SINGLE_SUCCESS;
            })
            .then(Commands.argument("args", StringArgumentType.greedyString())
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(plugin.getPrefix(sender).append(Component.text("Only players can use this command.", NamedTextColor.RED)));
                        return Command.SINGLE_SUCCESS;
                    }
                    String argsStr = StringArgumentType.getString(ctx, "args");
                    String[] args = argsStr.split(" ");
                    action.accept(player, args);
                    return Command.SINGLE_SUCCESS;
                })
            )
            .build();
            
        commands.register(node, description, aliases);
    }

    private static void registerLegacyCommand(Commands commands, String name, String description, List<String> aliases, CommandExecutor executor) {
        LiteralCommandNode<CommandSourceStack> node = Commands.literal(name)
            .executes(ctx -> {
                CommandSender sender = ctx.getSource().getSender();
                executor.onCommand(sender, null, name, new String[0]);
                return Command.SINGLE_SUCCESS;
            })
            .then(Commands.argument("args", StringArgumentType.greedyString())
                .suggests((ctx, builder) -> {
                    if (executor instanceof org.bukkit.command.TabCompleter) {
                        String input = ctx.getInput();
                        int lastSpace = input.lastIndexOf(' ');
                        String[] args = input.substring(input.indexOf(' ') + 1).split(" ", -1);
                        List<String> completions = ((org.bukkit.command.TabCompleter) executor).onTabComplete(ctx.getSource().getSender(), null, name, args);
                        if (completions != null) {
                            String lastArg = args.length > 0 ? args[args.length - 1] : "";
                            com.mojang.brigadier.suggestion.SuggestionsBuilder offsetBuilder = builder.createOffset(builder.getStart() + lastSpace + 1 - builder.getStart());
                            for (String c : completions) {
                                if (c.toLowerCase().startsWith(lastArg.toLowerCase())) {
                                    offsetBuilder.suggest(c);
                                }
                            }
                            return offsetBuilder.buildFuture();
                        }
                    }
                    return builder.buildFuture();
                })
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    String argsStr = StringArgumentType.getString(ctx, "args");
                    String[] args = argsStr.split(" ");
                    executor.onCommand(sender, null, name, args);
                    return Command.SINGLE_SUCCESS;
                })
            )
            .build();
            
        commands.register(node, description, aliases);
    }
}
