package org.bcnlab.beaconLabsVelocityLink.commands;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bcnlab.beaconLabsVelocityLink.BeaconLabsVelocityLink;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Collection;
import java.util.List;

@NullMarked
public final class NickCommand implements BasicCommand {
    public static final String PERMISSION = "beaconlabs.velocitylink.nick";

    private final BeaconLabsVelocityLink plugin;

    public NickCommand(BeaconLabsVelocityLink plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getPrefix(sender).append(Component.text("Only players can use this command.", NamedTextColor.GRAY)));
            return;
        }
        plugin.handleNick(player, args);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        if (args.length <= 1) {
            String input = args.length == 0 ? "" : args[0];
            return List.of("random").stream()
                    .filter(value -> value.startsWith(input.toLowerCase(java.util.Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return sender.hasPermission(PERMISSION);
    }
}
