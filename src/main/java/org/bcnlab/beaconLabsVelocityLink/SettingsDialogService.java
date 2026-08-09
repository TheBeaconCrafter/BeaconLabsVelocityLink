package org.bcnlab.beaconLabsVelocityLink;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class SettingsDialogService implements PluginMessageListener, Listener, CommandExecutor {
    public static final String CHANNEL = "beaconlabs:settings_dialog";
    private final BeaconLabsVelocityLink plugin;
    
    private final NamespacedKey ACTION_KEY;

    private final Map<UUID, String> msgPrivacyCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> friendReqCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> friendServerCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> friendsJoinAlertCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> joinSummaryCache = new ConcurrentHashMap<>();

    public SettingsDialogService(BeaconLabsVelocityLink plugin) {
        this.plugin = plugin;
        this.ACTION_KEY = new NamespacedKey(plugin, "setting_action");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            String uuidStr = in.readUTF();
            String msgPrivacy = in.readUTF();
            String friendRequests = in.readUTF();
            String friendServer = "everyone";
            if (in.available() > 0) friendServer = in.readUTF();
            String friendsJoinAlert = "on";
            if (in.available() > 0) friendsJoinAlert = in.readUTF();
            String joinSummary = "off";
            if (in.available() > 0) joinSummary = in.readUTF();
            
            UUID uuid = UUID.fromString(uuidStr);
            if (player.getUniqueId().equals(uuid)) {
                msgPrivacyCache.put(uuid, msgPrivacy);
                friendReqCache.put(uuid, friendRequests);
                friendServerCache.put(uuid, friendServer);
                friendsJoinAlertCache.put(uuid, friendsJoinAlert);
                joinSummaryCache.put(uuid, joinSummary);
                Bukkit.getScheduler().runTask(plugin, () -> openMainMenu(player));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse settings dialog message: " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player) {
            openMainMenu(player);
        }
        return true;
    }

    private void fillBorder(Inventory inv) {
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = border.getItemMeta();
        meta.displayName(Component.empty());
        border.setItemMeta(meta);
        int size = inv.getSize();
        for (int i = 0; i < size; i++) {
            if (i < 9 || i >= size - 9 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, border);
            }
        }
    }

    private ItemStack createSettingItem(Material mat, String name, NamedTextColor color, String description, String currentValue, String action) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(description, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Currently: ", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(formatValue(currentValue), getColorForValue(currentValue)).decoration(TextDecoration.ITALIC, false)));
        lore.add(Component.empty());
        lore.add(Component.text("Click to toggle", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }
    
    private ItemStack createMenuNav(Material mat, String name, NamedTextColor color, String description, String action) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        if (description != null) {
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(description, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        }
        meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private void openMainMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, Component.text("Settings"));
        fillBorder(gui);
        
        gui.setItem(11, createMenuNav(Material.IRON_DOOR, "Privacy Settings", NamedTextColor.AQUA, "Manage messages, friend requests...", "menu_privacy"));
        gui.setItem(15, createMenuNav(Material.BELL, "Alerts & Notifications", NamedTextColor.YELLOW, "Manage join alerts and summaries...", "menu_alerts"));
        
        player.openInventory(gui);
    }

    private void openPrivacyMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 36, Component.text("Settings > Privacy"));
        fillBorder(gui);
        
        String msgPrivacy = msgPrivacyCache.getOrDefault(player.getUniqueId(), "everyone");
        String friendReq = friendReqCache.getOrDefault(player.getUniqueId(), "everyone");
        String friendServer = friendServerCache.getOrDefault(player.getUniqueId(), "friends_only");
        
        gui.setItem(11, createSettingItem(Material.PAPER, "Private Messages", NamedTextColor.AQUA, "Toggle who can send you private messages.", msgPrivacy, "toggle_msg_privacy"));
        gui.setItem(13, createSettingItem(Material.POPPY, "Friend Requests", NamedTextColor.RED, "Toggle who can send you friend requests.", friendReq, "toggle_friend_requests"));
        gui.setItem(15, createSettingItem(Material.COMPASS, "Server Privacy", NamedTextColor.LIGHT_PURPLE, "Toggle who can see what server you're on.", friendServer, "toggle_friend_server"));
        
        gui.setItem(31, createMenuNav(Material.SPECTRAL_ARROW, "Back", NamedTextColor.GOLD, null, "menu_main"));
        
        player.openInventory(gui);
    }
    
    private void openAlertsMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 36, Component.text("Settings > Alerts"));
        fillBorder(gui);
        
        String joinAlert = friendsJoinAlertCache.getOrDefault(player.getUniqueId(), "on");
        String joinSum = joinSummaryCache.getOrDefault(player.getUniqueId(), "off");
        
        gui.setItem(11, createSettingItem(Material.NOTE_BLOCK, "Friends Join Alert", NamedTextColor.GOLD, "See when friends join the server.", joinAlert, "toggle_friends_join_alert"));
        gui.setItem(15, createSettingItem(Material.WRITABLE_BOOK, "Join Summary", NamedTextColor.AQUA, "Get a summary of online friends when you join.", joinSum, "toggle_join_summary"));
        
        gui.setItem(31, createMenuNav(Material.SPECTRAL_ARROW, "Back", NamedTextColor.GOLD, null, "menu_main"));
        
        player.openInventory(gui);
    }

    private void sendUpdateToProxy(Player player, String key, String value) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DataOutputStream data = new DataOutputStream(out);
            data.writeUTF(player.getUniqueId().toString());
            data.writeUTF(key);
            data.writeUTF(value);
            player.sendPluginMessage(plugin, "beaconlabs:settings_update", out.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String cycleMsgPrivacy(String current) {
        return switch (current.toLowerCase()) {
            case "everyone" -> "friends_only";
            case "friends_only" -> "nobody";
            default -> "everyone";
        };
    }

    private String cycleFriendReq(String current) {
        return switch (current.toLowerCase()) {
            case "everyone" -> "nobody";
            default -> "everyone";
        };
    }

    private String cycleServerPrivacy(String current) {
        return switch (current.toLowerCase()) {
            case "friends_only" -> "nobody";
            default -> "friends_only";
        };
    }
    
    private String cycleOnOff(String current) {
        return switch (current.toLowerCase()) {
            case "on", "true" -> "off";
            default -> "on";
        };
    }

    private NamedTextColor getColorForValue(String value) {
        if (value == null) return NamedTextColor.GRAY;
        return switch (value.toLowerCase()) {
            case "everyone", "on", "true" -> NamedTextColor.GREEN;
            case "friends_only" -> NamedTextColor.GOLD;
            case "nobody", "off", "false" -> NamedTextColor.RED;
            default -> NamedTextColor.WHITE;
        };
    }

    private String formatValue(String value) {
        if (value == null) return "Unknown";
        return switch (value.toLowerCase()) {
            case "everyone" -> "everyone";
            case "friends_only" -> "friends only";
            case "nobody" -> "nobody";
            case "on", "true" -> "on";
            case "off", "false" -> "off";
            default -> value;
        };
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String titleStr = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (titleStr.startsWith("Settings")) {
            event.setCancelled(true);
            
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;
            
            String action = clicked.getItemMeta().getPersistentDataContainer().get(ACTION_KEY, PersistentDataType.STRING);
            if (action == null) return;
            
            if (!(event.getWhoClicked() instanceof Player player)) return;
            
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            
            if (action.equals("menu_main")) {
                openMainMenu(player);
                return;
            } else if (action.equals("menu_privacy")) {
                openPrivacyMenu(player);
                return;
            } else if (action.equals("menu_alerts")) {
                openAlertsMenu(player);
                return;
            }
            
            if (action.equals("toggle_msg_privacy")) {
                String next = cycleMsgPrivacy(msgPrivacyCache.getOrDefault(player.getUniqueId(), "everyone"));
                msgPrivacyCache.put(player.getUniqueId(), next);
                sendUpdateToProxy(player, "msg_privacy", next);
                openPrivacyMenu(player);
            } else if (action.equals("toggle_friend_requests")) {
                String next = cycleFriendReq(friendReqCache.getOrDefault(player.getUniqueId(), "everyone"));
                friendReqCache.put(player.getUniqueId(), next);
                sendUpdateToProxy(player, "friend_requests", next);
                openPrivacyMenu(player);
            } else if (action.equals("toggle_friend_server")) {
                String next = cycleServerPrivacy(friendServerCache.getOrDefault(player.getUniqueId(), "friends_only"));
                friendServerCache.put(player.getUniqueId(), next);
                sendUpdateToProxy(player, "friend_server", next);
                openPrivacyMenu(player);
            } else if (action.equals("toggle_friends_join_alert")) {
                String next = cycleOnOff(friendsJoinAlertCache.getOrDefault(player.getUniqueId(), "on"));
                friendsJoinAlertCache.put(player.getUniqueId(), next);
                sendUpdateToProxy(player, "friends_join_alert", next);
                openAlertsMenu(player);
            } else if (action.equals("toggle_join_summary")) {
                String next = cycleOnOff(joinSummaryCache.getOrDefault(player.getUniqueId(), "off"));
                joinSummaryCache.put(player.getUniqueId(), next);
                sendUpdateToProxy(player, "join_summary", next);
                openAlertsMenu(player);
            }
        }
    }
}
