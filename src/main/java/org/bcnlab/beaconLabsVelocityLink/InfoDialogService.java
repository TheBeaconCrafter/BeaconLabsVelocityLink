package org.bcnlab.beaconLabsVelocityLink;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.text.SimpleDateFormat;
import java.util.Date;

public class InfoDialogService implements PluginMessageListener, Listener {
    private final BeaconLabsVelocityLink plugin;
    public static final String CHANNEL = "beaconlabs:info_dialog";

    public InfoDialogService(BeaconLabsVelocityLink plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            String targetUuidStr = in.readUTF();
            String realName = in.readUTF();
            boolean isNickname = in.readBoolean();
            String nickname = in.readUTF();
            
            long playtimeMs = in.readLong();
            long lastSeenMs = in.readLong();
            
            boolean online = in.readBoolean();
            String proxy = "Unknown";
            String server = "Unknown";
            long ping = 0;
            String client = "Unknown";
            
            if (online) {
                proxy = in.readUTF();
                server = in.readUTF();
                ping = in.readLong();
                client = in.readUTF();
            } else {
                proxy = in.readUTF();
            }
            
            long activeBans = in.readLong();
            long activeMutes = in.readLong();

            String finalProxy = proxy;
            String finalServer = server;
            long finalPing = ping;
            String finalClient = client;
            
            Bukkit.getScheduler().runTask(plugin, () -> openInfoGui(player, targetUuidStr, realName, isNickname, nickname, playtimeMs, lastSeenMs, online, finalProxy, finalServer, finalPing, finalClient, activeBans, activeMutes));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse info dialog: " + e.getMessage());
        }
    }

    private String formatPlaytime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) return days + "d " + (hours % 24) + "h";
        if (hours > 0) return hours + "h " + (minutes % 60) + "m";
        if (minutes > 0) return minutes + "m";
        return seconds + "s";
    }

    private void openInfoGui(Player player, String targetUuidStr, String realName, boolean isNickname, String nickname, long playtimeMs, long lastSeenMs, boolean online, String proxy, String server, long ping, String client, long activeBans, long activeMutes) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text("Player Info: " + (isNickname ? nickname : realName)));

        // Background
        ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        bgMeta.displayName(Component.empty());
        bg.setItemMeta(bgMeta);
        for (int i = 0; i < 27; i++) inv.setItem(i, bg);

        // Player Head
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta headMeta = (SkullMeta) head.getItemMeta();
        headMeta.setOwningPlayer(Bukkit.getOfflinePlayer(UUID.fromString(targetUuidStr)));
        headMeta.displayName(Component.text(realName, NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("UUID: ", NamedTextColor.GRAY).append(Component.text(targetUuidStr, NamedTextColor.YELLOW)).decoration(TextDecoration.ITALIC, false));
        if (isNickname) {
            lore.add(Component.text("Playing as Nickname: ", NamedTextColor.GRAY).append(Component.text(nickname, NamedTextColor.RED)).decoration(TextDecoration.ITALIC, false));
        }
        headMeta.lore(lore);
        head.setItemMeta(headMeta);
        inv.setItem(11, head);

        // Clock (Time Stats)
        ItemStack clock = new ItemStack(Material.CLOCK);
        ItemMeta clockMeta = clock.getItemMeta();
        clockMeta.displayName(Component.text("Activity", NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> clockLore = new ArrayList<>();
        clockLore.add(Component.text("Playtime: ", NamedTextColor.GRAY).append(Component.text(formatPlaytime(playtimeMs), NamedTextColor.YELLOW)).decoration(TextDecoration.ITALIC, false));
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        if (lastSeenMs > 0) {
            clockLore.add(Component.text("Last Seen: ", NamedTextColor.GRAY).append(Component.text(dateFormat.format(new Date(lastSeenMs)), NamedTextColor.YELLOW)).decoration(TextDecoration.ITALIC, false));
        }
        clockMeta.lore(clockLore);
        clock.setItemMeta(clockMeta);
        inv.setItem(13, clock);

        // Compass (Network Stats)
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta compassMeta = compass.getItemMeta();
        compassMeta.displayName(Component.text("Network", NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> compassLore = new ArrayList<>();
        if (online) {
            compassLore.add(Component.text("Status: ", NamedTextColor.GRAY).append(Component.text("ONLINE", NamedTextColor.GREEN)).decoration(TextDecoration.ITALIC, false));
            compassLore.add(Component.text("Proxy: ", NamedTextColor.GRAY).append(Component.text(proxy, NamedTextColor.YELLOW)).decoration(TextDecoration.ITALIC, false));
            compassLore.add(Component.text("Server: ", NamedTextColor.GRAY).append(Component.text(server, NamedTextColor.YELLOW)).decoration(TextDecoration.ITALIC, false));
            compassLore.add(Component.text("Ping: ", NamedTextColor.GRAY).append(Component.text(ping + "ms", NamedTextColor.YELLOW)).decoration(TextDecoration.ITALIC, false));
            compassLore.add(Component.text("Client: ", NamedTextColor.GRAY).append(Component.text(client, NamedTextColor.YELLOW)).decoration(TextDecoration.ITALIC, false));
        } else {
            compassLore.add(Component.text("Status: ", NamedTextColor.GRAY).append(Component.text("OFFLINE", NamedTextColor.RED)).decoration(TextDecoration.ITALIC, false));
            if (proxy != null && !proxy.isEmpty()) {
                compassLore.add(Component.text("Proxy: ", NamedTextColor.GRAY).append(Component.text(proxy, NamedTextColor.YELLOW)).decoration(TextDecoration.ITALIC, false));
            }
        }
        compassMeta.lore(compassLore);
        compass.setItemMeta(compassMeta);
        inv.setItem(15, compass);

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().title().toString().contains("Player Info")) {
            event.setCancelled(true);
        }
    }
}
