package org.bcnlab.beaconLabsVelocityLink.commands;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bcnlab.beaconLabsVelocityLink.BeaconLabsVelocityLink;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Collection;
import java.util.List;

@NullMarked
public final class UnnickCommand implements BasicCommand {
    public static final String PERMISSION = "beaconlabs.velocitylink.unnick";
    public static final String OTHERS_PERMISSION = "beaconlabs.admin.unnick";

    private final BeaconLabsVelocityLink plugin;

    public UnnickCommand(BeaconLabsVelocityLink plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getPrefix(sender).append(Component.text("Only players can use this command.", NamedTextColor.GRAY)));
            return;
        }
        if (args.length > 0 && !player.hasPermission(OTHERS_PERMISSION)) {
            player.sendMessage(plugin.getPrefix(player).append(Component.text("You do not have permission to unnick other players.", NamedTextColor.RED)));
            return;
        }
        plugin.handleNickRemove(player, args);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        if (args.length <= 1 && stack.getSender().hasPermission(OTHERS_PERMISSION)) {
            String input = args.length == 0 ? "" : args[0];
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.regionMatches(true, 0, input, 0, input.length()))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }
        return List.of();
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return sender.hasPermission(PERMISSION) || sender.hasPermission(OTHERS_PERMISSION);
    }
}
