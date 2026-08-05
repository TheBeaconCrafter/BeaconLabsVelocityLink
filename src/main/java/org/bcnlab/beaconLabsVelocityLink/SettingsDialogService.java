package org.bcnlab.beaconLabsVelocityLink;

import org.bukkit.Bukkit;
import org.bukkit.Material;
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
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SettingsDialogService implements PluginMessageListener, Listener, CommandExecutor {
    public static final String CHANNEL = "beaconlabs:settings_dialog";
    private final BeaconLabsVelocityLink plugin;
    private final net.kyori.adventure.text.Component title = net.kyori.adventure.text.Component.text("Settings").color(net.kyori.adventure.text.format.NamedTextColor.DARK_AQUA);

    // Track current settings for open GUIs
    private final Map<UUID, String> msgPrivacyCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> friendReqCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> friendServerCache = new ConcurrentHashMap<>();

    public SettingsDialogService(BeaconLabsVelocityLink plugin) {
        this.plugin = plugin;
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
            if (in.available() > 0) {
                friendServer = in.readUTF();
            }
            UUID uuid = UUID.fromString(uuidStr);
            if (player.getUniqueId().equals(uuid)) {
                msgPrivacyCache.put(uuid, msgPrivacy);
                friendReqCache.put(uuid, friendRequests);
                friendServerCache.put(uuid, friendServer);
                Bukkit.getScheduler().runTask(plugin, () -> openSettingsGUI(player));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse settings dialog message: " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player) {
            // When run locally without args, it shouldn't really work since it lacks settings,
            // but we can just open it with defaults or whatever is in cache.
            openSettingsGUI(player);
        }
        return true;
    }

    private void openSettingsGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, title);
        
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        if (borderMeta != null) {
            borderMeta.displayName(net.kyori.adventure.text.Component.text(" "));
            border.setItemMeta(borderMeta);
        }
        for (int i = 0; i < 27; i++) {
            if (i < 9 || i >= 18 || i % 9 == 0 || i % 9 == 8) {
                gui.setItem(i, border);
            }
        }
        
        String msgPrivacy = msgPrivacyCache.getOrDefault(player.getUniqueId(), "everyone");
        String friendReq = friendReqCache.getOrDefault(player.getUniqueId(), "everyone");
        String friendServer = friendServerCache.getOrDefault(player.getUniqueId(), "everyone");

        // Private Messages
        ItemStack pmItem = new ItemStack(Material.PAPER);
        ItemMeta pmMeta = pmItem.getItemMeta();
        if (pmMeta != null) {
            pmMeta.displayName(net.kyori.adventure.text.Component.text("Private Messages", net.kyori.adventure.text.format.NamedTextColor.AQUA).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(net.kyori.adventure.text.Component.text("Toggle who can send you private messages.", net.kyori.adventure.text.format.NamedTextColor.GRAY).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            lore.add(net.kyori.adventure.text.Component.empty());
            lore.add(net.kyori.adventure.text.Component.text("Currently: ", net.kyori.adventure.text.format.NamedTextColor.YELLOW).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
                    .append(net.kyori.adventure.text.Component.text(formatValue(msgPrivacy), net.kyori.adventure.text.format.NamedTextColor.GREEN).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, true)));
            lore.add(net.kyori.adventure.text.Component.empty());
            lore.add(net.kyori.adventure.text.Component.text("Click to toggle", net.kyori.adventure.text.format.NamedTextColor.YELLOW).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            pmMeta.lore(lore);
            pmItem.setItemMeta(pmMeta);
        }
        gui.setItem(11, pmItem);
        
        // Friend Requests
        ItemStack frItem = new ItemStack(Material.POPPY);
        ItemMeta frMeta = frItem.getItemMeta();
        if (frMeta != null) {
            frMeta.displayName(net.kyori.adventure.text.Component.text("Friend Requests", net.kyori.adventure.text.format.NamedTextColor.RED).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(net.kyori.adventure.text.Component.text("Toggle who can send you friend requests.", net.kyori.adventure.text.format.NamedTextColor.GRAY).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            lore.add(net.kyori.adventure.text.Component.empty());
            lore.add(net.kyori.adventure.text.Component.text("Currently: ", net.kyori.adventure.text.format.NamedTextColor.YELLOW).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
                    .append(net.kyori.adventure.text.Component.text(formatValue(friendReq), net.kyori.adventure.text.format.NamedTextColor.GREEN).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, true)));
            lore.add(net.kyori.adventure.text.Component.empty());
            lore.add(net.kyori.adventure.text.Component.text("Click to toggle", net.kyori.adventure.text.format.NamedTextColor.YELLOW).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            frMeta.lore(lore);
            frItem.setItemMeta(frMeta);
        }
        gui.setItem(13, frItem);

        // Server Privacy
        ItemStack spItem = new ItemStack(Material.COMPASS);
        ItemMeta spMeta = spItem.getItemMeta();
        if (spMeta != null) {
            spMeta.displayName(net.kyori.adventure.text.Component.text("Server Privacy", net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(net.kyori.adventure.text.Component.text("Toggle who can see what server you're on.", net.kyori.adventure.text.format.NamedTextColor.GRAY).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            lore.add(net.kyori.adventure.text.Component.empty());
            lore.add(net.kyori.adventure.text.Component.text("Currently: ", net.kyori.adventure.text.format.NamedTextColor.YELLOW).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
                    .append(net.kyori.adventure.text.Component.text(formatValue(friendServer), net.kyori.adventure.text.format.NamedTextColor.GREEN).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, true)));
            lore.add(net.kyori.adventure.text.Component.empty());
            lore.add(net.kyori.adventure.text.Component.text("Click to toggle", net.kyori.adventure.text.format.NamedTextColor.YELLOW).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            spMeta.lore(lore);
            spItem.setItemMeta(spMeta);
        }
        gui.setItem(15, spItem);
        
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

    private String formatValue(String value) {
        if (value == null) return "Unknown";
        return switch (value.toLowerCase()) {
            case "everyone" -> "Everyone";
            case "friends_only" -> "Friends Only";
            case "nobody" -> "Nobody";
            default -> value;
        };
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().title().equals(title)) {
            event.setCancelled(true);
            
            if (event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.GRAY_STAINED_GLASS_PANE && event.getCurrentItem().getType() != Material.AIR) {
                if (event.getWhoClicked() instanceof Player player) {
                    
                    if (event.getRawSlot() == 11) {
                        // PMs
                        String current = msgPrivacyCache.getOrDefault(player.getUniqueId(), "everyone");
                        String next = cycleMsgPrivacy(current);
                        msgPrivacyCache.put(player.getUniqueId(), next);
                        sendUpdateToProxy(player, "msg_privacy", next);
                        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                        openSettingsGUI(player); // refresh
                    } else if (event.getRawSlot() == 15) {
                        // Friends
                        String current = friendReqCache.getOrDefault(player.getUniqueId(), "everyone");
                        String next = cycleFriendReq(current);
                        friendReqCache.put(player.getUniqueId(), next);
                        sendUpdateToProxy(player, "friend_requests", next);
                        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                        openSettingsGUI(player); // refresh
                    } else if (event.getRawSlot() == 15) {
                        // Server Privacy
                        String current = friendServerCache.getOrDefault(player.getUniqueId(), "everyone");
                        String next = cycleMsgPrivacy(current); // Uses same cycle as msgPrivacy (everyone -> friends_only -> nobody)
                        friendServerCache.put(player.getUniqueId(), next);
                        sendUpdateToProxy(player, "friend_server", next);
                        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                        openSettingsGUI(player); // refresh
                    }
                }
            }
        }
    }
}
