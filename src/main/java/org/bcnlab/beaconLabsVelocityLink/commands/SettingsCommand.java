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
public final class SettingsCommand implements BasicCommand {
    public static final String PERMISSION = "beaconlabs.velocitylink.settings";

    private final BeaconLabsVelocityLink plugin;

    public SettingsCommand(BeaconLabsVelocityLink plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getPrefix(sender).append(Component.text("Only players can use this command.", NamedTextColor.GRAY)));
            return;
        }
        plugin.openSettings(player, args);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        return List.of();
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return sender.hasPermission(PERMISSION);
    }
}
