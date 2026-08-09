package org.bcnlab.beaconLabsVelocityLink;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.command.CommandSender;
import org.bukkit.Bukkit;
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
    private String legacyPrefixString;
    private Component legacyPrefix;

    private VisualStateService visualStateService;
    private FriendDialogService friendDialogService;
    private InfoDialogService infoDialogService;
    private ReportDialogService reportDialogService;
    private SettingsDialogService settingsDialogService;

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void onEnable() {
        visualStateService = new VisualStateService(this);
        friendDialogService = new FriendDialogService(this);
        infoDialogService = new InfoDialogService(this);
        reportDialogService = new ReportDialogService(this);
        settingsDialogService = new SettingsDialogService(this);
        
        IpInfoDialogService ipInfoDialogService = new IpInfoDialogService(this);
        PunishmentsDialogService punishmentsDialogService = new PunishmentsDialogService(this);
        CloudDialogService cloudDialogService = new CloudDialogService(this);

        getServer().getPluginManager().registerEvents(visualStateService, this);
        
        getServer().getMessenger().registerIncomingPluginChannel(this, VisualStateService.CHANNEL, visualStateService);
        getServer().getMessenger().registerOutgoingPluginChannel(this, VisualStateService.CHANNEL);
        
        getServer().getMessenger().registerIncomingPluginChannel(this, FriendDialogService.CHANNEL, friendDialogService);
        getServer().getMessenger().registerIncomingPluginChannel(this, SettingsDialogService.CHANNEL, settingsDialogService);
        getServer().getMessenger().registerIncomingPluginChannel(this, IpInfoDialogService.CHANNEL, ipInfoDialogService);
        getServer().getMessenger().registerIncomingPluginChannel(this, PunishmentsDialogService.CHANNEL, punishmentsDialogService);
        getServer().getMessenger().registerIncomingPluginChannel(this, CloudDialogService.CHANNEL, cloudDialogService);
        
        getServer().getMessenger().registerOutgoingPluginChannel(this, "beaconlabs:settings_update");
        getServer().getMessenger().registerOutgoingPluginChannel(this, CloudDialogService.ACTION_CHANNEL);
        
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            org.bcnlab.beaconLabsVelocityLink.utils.CommandRegistry.registerAll(this, event.registrar());
        });

        // Periodically announce presence to Velocity if there are players online
        getServer().getScheduler().runTaskTimer(this, () -> {
            Player p = org.bukkit.Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
            if (p != null) {
                try {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    DataOutputStream data = new DataOutputStream(out);
                    data.writeUTF(p.getUniqueId().toString());
                    data.writeUTF("LINK_HELLO");
                    data.writeUTF("");
                    data.writeUTF("");
                    p.sendPluginMessage(this, VisualStateService.CHANNEL, out.toByteArray());
                } catch (Exception e) {}
            }
        }, 100L, 200L); // every 10 seconds
        
        getServer().getMessenger().registerOutgoingPluginChannel(this, "beaconlabs:friend_request");
        getServer().getMessenger().registerOutgoingPluginChannel(this, "beaconlabs:proxy_command");
        
        getServer().getMessenger().registerIncomingPluginChannel(this, InfoDialogService.CHANNEL, infoDialogService);
        getServer().getMessenger().registerIncomingPluginChannel(this, ReportDialogService.CHANNEL, reportDialogService);
        getServer().getMessenger().registerOutgoingPluginChannel(this, ReportDialogService.CHANNEL);
        
        saveDefaultConfig();
        prefixString = getConfig().getString("prefix", "<gold>BeaconLabs</gold> <dark_gray>»</dark_gray> ");
        prefix = MiniMessage.miniMessage().deserialize(prefixString);
        
        legacyPrefixString = getConfig().getString("legacy-prefix", "&6BeaconLabs &8» &7");
        legacyPrefix = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(legacyPrefixString);

        getLogger().info("BeaconLabsVelocityLink enabled.");
    }

    public Component getPrefix() {
        return prefix;
    }

    public Component getPrefix(Player player) {
        if (Bukkit.getPluginManager().isPluginEnabled("ViaVersion")) {
            try {
                int protocol = com.viaversion.viaversion.api.Via.getAPI().getPlayerVersion(player.getUniqueId());
                if (protocol < 735) {
                    return legacyPrefix;
                }
            } catch (Exception e) {}
        }
        return prefix;
    }

    public VisualStateService getVisualStateService() {
        return visualStateService;
    }

    @Override
    public void onDisable() {
        getLogger().info("BeaconLabsVelocityLink disabled.");
    }



    public boolean handleNick(Player player, String[] args) {
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
        
        if (nickname == null) {
            visualStateService.applyNick(player, null, null);
            sendToProxy(player, "NICK", null, null);
            player.sendMessage(getPrefix(player).append(Component.text("Nickname removed.", NamedTextColor.GRAY)));
        } else {
            player.sendMessage(getPrefix(player).append(Component.text("Requesting nickname...", NamedTextColor.GRAY)));
            // Send NICK_REQUEST instead of NICK, pass fakeRank in skinSource field for now
            sendToProxy(player, "NICK_REQUEST", nickname, fakeRank == null ? "" : fakeRank);
        }
        return true;
    }

    public boolean handleNickRemove(Player player, String[] args) {
        if (args.length > 0 && player.hasPermission("beaconlabs.admin.unnick")) {
            Player target = Bukkit.getPlayer(args[0]);
            if (target != null) {
                visualStateService.applyNick(target, null, null);
                sendToProxy(target, "NICK", null, null);
                player.sendMessage(getPrefix(player).append(Component.text("Removed nickname for ", NamedTextColor.GRAY)).append(Component.text(target.getName(), NamedTextColor.GOLD)));
            } else {
                player.sendMessage(getPrefix(player).append(Component.text("Player not found.", NamedTextColor.RED)));
            }
        } else {
            visualStateService.applyNick(player, null, null);
            sendToProxy(player, "NICK", null, null);
            player.sendMessage(getPrefix(player).append(Component.text("Nickname removed.", NamedTextColor.GRAY)));
        }
        return true;
    }

    public boolean handleVanish(Player player, String[] args) {
        getLogger().info("[CMD] /vanish player=" + player.getName() + " uuid=" + player.getUniqueId());
        visualStateService.toggleVanish(player);
        sendToProxy(player, "VANISH", "", "");
        player.sendMessage(getPrefix(player).append(Component.text("Vanish toggled.", NamedTextColor.GREEN)));
        return true;
    }

    
    public void handleSettings(org.bukkit.entity.Player player, String[] args) {
        settingsDialogService.onCommand(player, null, "settings", args);
    }
    public void handleFriends(org.bukkit.entity.Player player, String[] args) {
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream data = new java.io.DataOutputStream(out);
            data.writeUTF(player.getUniqueId().toString());
            player.sendPluginMessage(this, "beaconlabs:friend_request", out.toByteArray());
        } catch (Exception e) {}
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
            
            String rank = "";
            if (visualStateService != null) {
                rank = visualStateService.getFakeRank(player);
            }
            data.writeUTF(rank != null ? rank : "");
            
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
