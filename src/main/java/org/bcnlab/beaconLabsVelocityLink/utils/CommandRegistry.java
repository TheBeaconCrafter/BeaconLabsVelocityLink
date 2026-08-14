package org.bcnlab.beaconLabsVelocityLink.utils;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.Commands;
import org.bcnlab.beaconLabsVelocityLink.BeaconLabsVelocityLink;
import org.bcnlab.beaconLabsVelocityLink.commands.FriendsCommand;
import org.bcnlab.beaconLabsVelocityLink.commands.LabsLinkCommand;
import org.bcnlab.beaconLabsVelocityLink.commands.NickCommand;
import org.bcnlab.beaconLabsVelocityLink.commands.SettingsCommand;
import org.bcnlab.beaconLabsVelocityLink.commands.UnnickCommand;
import org.bcnlab.beaconLabsVelocityLink.commands.VanishCommand;

import java.util.List;

/** Registers every command through Paper's native BasicCommand lifecycle API. */
public final class CommandRegistry {
    private CommandRegistry() {
    }

    public static void registerAll(BeaconLabsVelocityLink plugin, Commands commands) {
        register(commands, "nick", "Change your nickname", List.of("nickname"), new NickCommand(plugin));
        register(commands, "unnick", "Remove your nickname", List.of(), new UnnickCommand(plugin));
        register(commands, "pvanish", "Hide yourself from other players", List.of("pv"), new VanishCommand(plugin));
        register(commands, "settings", "Open your settings", List.of(), new SettingsCommand(plugin));
        register(commands, "friends", "Open your friends list", List.of("friend", "f"), new FriendsCommand(plugin));
        register(commands, "labslink", "Prints plugin information", List.of(), new LabsLinkCommand(plugin));
    }

    private static void register(Commands commands, String name, String description,
                                 List<String> aliases, BasicCommand command) {
        commands.register(name, description, aliases, command);
    }
}
