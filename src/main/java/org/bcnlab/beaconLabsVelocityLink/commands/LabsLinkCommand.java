package org.bcnlab.beaconLabsVelocityLink.commands;

import org.bcnlab.beaconLabsVelocityLink.BeaconLabsVelocityLink;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class LabsLinkCommand implements CommandExecutor {

    private final BeaconLabsVelocityLink plugin;

    public LabsLinkCommand(BeaconLabsVelocityLink plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Component message = Component.text("BeaconLabsVelocityLink Version ", NamedTextColor.GRAY)
                .append(Component.text(plugin.getDescription().getVersion(), NamedTextColor.GOLD))
                .append(Component.text(" by ItsBeacon", NamedTextColor.GRAY));
                
        sender.sendMessage(plugin.getPrefix(sender).append(message));
        return true;
    }
}
