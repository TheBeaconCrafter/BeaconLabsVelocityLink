package org.bcnlab.beaconLabsVelocityLink;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.format.NamedTextColor;

public final class BeaconLabsVelocityLink extends JavaPlugin {

    private String prefixString;
    private Component prefix;

    private VisualStateService visualStateService;
    private FriendDialogService friendDialogService;
    private SettingsDialogService settingsDialogService;

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void onEnable() {
        visualStateService = new VisualStateService(this);
        friendDialogService = new FriendDialogService(this);
        settingsDialogService = new SettingsDialogService(this);

        getServer().getPluginManager().registerEvents(visualStateService, this);
        
        getServer().getMessenger().registerIncomingPluginChannel(this, VisualStateService.CHANNEL, visualStateService);
        getServer().getMessenger().registerOutgoingPluginChannel(this, VisualStateService.CHANNEL);
        
        getServer().getMessenger().registerIncomingPluginChannel(this, FriendDialogService.CHANNEL, friendDialogService);
        getServer().getMessenger().registerIncomingPluginChannel(this, SettingsDialogService.CHANNEL, settingsDialogService);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "beaconlabs:settings_update");
        
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            // /nick [name|random]
            commands.registrar().register(
                Commands.literal("nick")
                    .executes(ctx -> {
                        CommandSender sender = ctx.getSource().getSender();
                        if (!(sender instanceof Player player)) {
                            sender.sendMessage(prefix.append(Component.text("Only players can use this command.", NamedTextColor.RED)));
                            return Command.SINGLE_SUCCESS;
                        }
                        handleNick(player, new String[0]);
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(Commands.argument("args", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            if (!(sender instanceof Player player)) {
                                sender.sendMessage(prefix.append(Component.text("Only players can use this command.", NamedTextColor.RED)));
                                return Command.SINGLE_SUCCESS;
                            }
                            String argsStr = StringArgumentType.getString(ctx, "args");
                            String[] args = argsStr.split(" ");
                            handleNick(player, args);
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                    .build(),
                "Set or randomize your nickname, appearance, and tab name",
                List.of()
            );

            // /vanish
            commands.registrar().register(
                Commands.literal("vanish")
                    .executes(ctx -> {
                        CommandSender sender = ctx.getSource().getSender();
                        if (!(sender instanceof Player player)) {
                            sender.sendMessage(prefix.append(Component.text("Only players can use this command.", NamedTextColor.RED)));
                            return Command.SINGLE_SUCCESS;
                        }
                        handleVanish(player);
                        return Command.SINGLE_SUCCESS;
                    })
                    .build(),
                "Toggle visibility in tab and world",
                List.of()
            );

            // /settings
            commands.registrar().register(
                Commands.literal("settings")
                    .executes(ctx -> {
                        CommandSender sender = ctx.getSource().getSender();
                        settingsDialogService.onCommand(sender, null, "settings", new String[0]);
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(Commands.argument("args", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            String argsStr = StringArgumentType.getString(ctx, "args");
                            String[] args = argsStr.split(" ");
                            settingsDialogService.onCommand(sender, null, "settings", args);
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                    .build(),
                "Open the settings GUI",
                List.of()
            );
            // /friends
            commands.registrar().register(
                Commands.literal("friends")
                    .executes(ctx -> {
                        CommandSender sender = ctx.getSource().getSender();
                        if (sender instanceof Player player) {
                            try {
                                ByteArrayOutputStream out = new ByteArrayOutputStream();
                                DataOutputStream data = new DataOutputStream(out);
                                data.writeUTF(player.getUniqueId().toString());
                                player.sendPluginMessage(BeaconLabsVelocityLink.this, "beaconlabs:friend_request", out.toByteArray());
                            } catch (Exception e) {}
                        }
                        return Command.SINGLE_SUCCESS;
                    }).build(),
                "Open friends GUI",
                List.of()
            );
        });
        
        getServer().getMessenger().registerOutgoingPluginChannel(this, "beaconlabs:friend_request");
        
        saveDefaultConfig();
        prefixString = getConfig().getString("prefix", "<gold>BeaconLabs</gold> <dark_gray>»</dark_gray> ");
        prefix = MiniMessage.miniMessage().deserialize(prefixString);

        getLogger().info("BeaconLabsVelocityLink enabled.");
    }

    public Component getPrefix() {
        return prefix;
    }

    public VisualStateService getVisualStateService() {
        return visualStateService;
    }

    @Override
    public void onDisable() {
        getLogger().info("BeaconLabsVelocityLink disabled.");
    }



    private boolean handleNick(Player player, String[] args) {
        String nickname;
        String fakeRank = null;
        if (args.length == 0) {
            if (visualStateService.isNicked(player)) {
                nickname = null;
            } else {
                nickname = nicknames[ThreadLocalRandom.current().nextInt(nicknames.length)];
            }
        } else if ("random".equals(args[0])) {
            nickname = nicknames[ThreadLocalRandom.current().nextInt(nicknames.length)];
            if (args.length > 1) fakeRank = args[1];
        } else {
            nickname = args[0];
            if (args.length > 1) fakeRank = args[1];
        }
        getLogger().info("[CMD] /nick player=" + player.getName() + " uuid=" + player.getUniqueId() + " nickname=" + nickname + " rank=" + fakeRank);
        visualStateService.applyNick(player, nickname, fakeRank);
        sendToProxy(player, "NICK", nickname, nickname);
        if (nickname == null) {
            player.sendMessage(prefix.append(Component.text("Nickname removed.", NamedTextColor.GREEN)));
        } else {
            if (fakeRank != null) {
                player.sendMessage(prefix.append(Component.text("Nickname set to " + nickname + " with rank " + fakeRank, NamedTextColor.GREEN)));
            } else {
                player.sendMessage(prefix.append(Component.text("Nickname set to " + nickname, NamedTextColor.GREEN)));
            }
        }
        return true;
    }

    private boolean handleVanish(Player player) {
        getLogger().info("[CMD] /vanish player=" + player.getName() + " uuid=" + player.getUniqueId());
        visualStateService.toggleVanish(player);
        sendToProxy(player, "VANISH", "", "");
        player.sendMessage(prefix.append(Component.text("Vanish toggled.", NamedTextColor.GREEN)));
        return true;
    }

    private void sendToProxy(Player player, String action, String nickname, String skinSource) {
        getLogger().info("[CMD] sendToProxy player=" + player.getName() + " action=" + action + " nick=" + nickname + " skin=" + skinSource);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DataOutputStream data = new DataOutputStream(out);
            data.writeUTF(player.getUniqueId().toString());
            data.writeUTF(action);
            data.writeUTF(nickname != null ? nickname : "");
            data.writeUTF(skinSource != null ? skinSource : "");
            data.flush();
            byte[] bytes = out.toByteArray();
            getLogger().info("[CMD] Sending " + bytes.length + " bytes to proxy on channel " + VisualStateService.CHANNEL);
            player.sendPluginMessage(this, VisualStateService.CHANNEL, bytes);
            getLogger().info("[CMD] Plugin message sent successfully");
        } catch (IOException e) {
            getLogger().warning("[CMD] Failed to send visual state: " + e.getMessage());
        }
    }

    private static final String[] nicknames = new String[]{"Alex", "Steve", "Herobrine", "Notch", "Technoblade", "Dream", "Xisuma", "Hypnotize", "AntVenom", "CaptainSparklez"};
}
