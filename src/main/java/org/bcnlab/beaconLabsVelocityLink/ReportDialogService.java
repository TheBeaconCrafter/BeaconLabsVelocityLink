package org.bcnlab.beaconLabsVelocityLink;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ReportDialogService implements PluginMessageListener, Listener {
    private final BeaconLabsVelocityLink plugin;
    public static final String CHANNEL = "beaconlabs:report_dialog";
    private final NamespacedKey ACTION_KEY;
    
    // Store states
    private final Map<UUID, String> reportingTarget = new HashMap<>();

    public ReportDialogService(BeaconLabsVelocityLink plugin) {
        this.plugin = plugin;
        this.ACTION_KEY = new NamespacedKey(plugin, "action");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            String type = in.readUTF();

            if ("REPORT".equals(type)) {
                String target = in.readUTF();
                Bukkit.getScheduler().runTask(plugin, () -> openReportGui(player, target));
            } else if ("REPORTS".equals(type)) {
                int size = in.readInt();
                java.util.List<String> reports = new java.util.ArrayList<>();
                for (int i = 0; i < size; i++) {
                    int id = in.readInt();
                    String reporter = in.readUTF();
                    String reported = in.readUTF();
                    String reason = in.readUTF();
                    String status = in.readUTF();
                    reports.add(id + ":" + reporter + ":" + reported + ":" + reason + ":" + status);
                }
                Bukkit.getScheduler().runTask(plugin, () -> openReportsListGui(player, reports));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse report dialog: " + e.getMessage());
        }
    }

    private void openReportGui(Player player, String target) {
        if (target == null || target.isBlank()) {
            player.sendMessage(plugin.getPrefix(player).append(Component.text("Please specify a player to report: /report <player>", NamedTextColor.RED)));
            return;
        }
        
        reportingTarget.put(player.getUniqueId(), target);
        
        Inventory inv = Bukkit.createInventory(null, 27, Component.text("Report: " + target));

        // Background
        ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        bgMeta.displayName(Component.empty());
        bg.setItemMeta(bgMeta);
        for (int i = 0; i < 27; i++) inv.setItem(i, bg);

        // 6 Reasons
        inv.setItem(10, createReasonItem(Material.DIAMOND_SWORD, "Hacking", "report_reason_hacking"));
        inv.setItem(11, createReasonItem(Material.PAPER, "Spam", "report_reason_spam"));
        inv.setItem(12, createReasonItem(Material.ROTTEN_FLESH, "Harassment", "report_reason_harassment"));
        inv.setItem(14, createReasonItem(Material.BOOK, "Inappropriate Language", "report_reason_language"));
        inv.setItem(15, createReasonItem(Material.TNT, "Griefing", "report_reason_griefing"));
        inv.setItem(16, createReasonItem(Material.REDSTONE_BLOCK, "Abuse", "report_reason_abuse"));
        
        inv.setItem(22, createReasonItem(Material.BARRIER, "Cancel", "report_cancel"));

        player.openInventory(inv);
    }

    private void openReportsListGui(Player player, java.util.List<String> reports) {
        Inventory inv = Bukkit.createInventory(null, 54, Component.text("Active Reports"));
        
        // Background
        ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        bgMeta.displayName(Component.empty());
        bg.setItemMeta(bgMeta);
        for (int i = 0; i < 54; i++) inv.setItem(i, bg);

        int slot = 0;
        for (String repStr : reports) {
            if (slot >= 45) break;
            
            String[] parts = repStr.split(":");
            if (parts.length >= 5) {
                String id = parts[0];
                String reporter = parts[1];
                String reported = parts[2];
                String reason = parts[3];
                String status = parts[4];
                
                ItemStack item = new ItemStack(Material.PAPER);
                ItemMeta meta = item.getItemMeta();
                meta.displayName(Component.text("Report #" + id, NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
                java.util.List<Component> lore = new java.util.ArrayList<>();
                lore.add(Component.text("Reporter: ", NamedTextColor.GRAY).append(Component.text(reporter, NamedTextColor.YELLOW)).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Reported: ", NamedTextColor.GRAY).append(Component.text(reported, NamedTextColor.RED)).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Reason: ", NamedTextColor.GRAY).append(Component.text(reason, NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Status: ", NamedTextColor.GRAY).append(Component.text(status, NamedTextColor.GREEN)).decoration(TextDecoration.ITALIC, false));
                meta.lore(lore);
                meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "view_report_" + id);
                item.setItemMeta(meta);
                
                inv.setItem(slot++, item);
            }
        }

        inv.setItem(49, createReasonItem(Material.BARRIER, "Close", "report_close"));
        
        player.openInventory(inv);
        
    }

    private ItemStack createReasonItem(Material mat, String name, String action) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        String title = event.getView().title().toString();
        if (title.contains("Report: ") || title.contains("Active Reports")) {
            event.setCancelled(true);
            
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;
            
            String action = clicked.getItemMeta().getPersistentDataContainer().get(ACTION_KEY, PersistentDataType.STRING);
            if (action == null) return;
            
            if (action.equals("report_cancel") || action.equals("report_close")) {
                player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.closeInventory();
                return;
            }
            
            if (action.startsWith("report_reason_")) {
                String reason = "Other";
                if (action.equals("report_reason_hacking")) reason = "Hacking";
                if (action.equals("report_reason_spam")) reason = "Spam";
                if (action.equals("report_reason_harassment")) reason = "Harassment";
                if (action.equals("report_reason_language")) reason = "Inappropriate Language";
                if (action.equals("report_reason_griefing")) reason = "Griefing";
                if (action.equals("report_reason_abuse")) reason = "Abuse";
                
                String target = reportingTarget.get(player.getUniqueId());
                if (target != null) {
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                    // Send command to Velocity
                    sendToProxyCommand(player, "report " + target + " " + reason);
                } else {
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
                player.closeInventory();
            }
        }
    }
    
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        reportingTarget.remove(event.getPlayer().getUniqueId());
    }
    
    private void sendToProxyCommand(Player player, String command) {
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream data = new java.io.DataOutputStream(out);
            data.writeUTF(command);
            player.sendPluginMessage(plugin, "beaconlabs:proxy_command", out.toByteArray());
        } catch (Exception e) {}
    }
}
