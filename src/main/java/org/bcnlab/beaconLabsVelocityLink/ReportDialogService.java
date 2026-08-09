package org.bcnlab.beaconLabsVelocityLink;
import org.bcnlab.beaconLabsVelocityLink.SoundUtil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class ReportDialogService implements PluginMessageListener, Listener, CommandExecutor {
    public static final String CHANNEL = "beaconlabs:report_dialog";
    private final BeaconLabsVelocityLink plugin;
    
    private final NamespacedKey ACTION_KEY;
    private final Map<UUID, String> reportingTarget = new ConcurrentHashMap<>();
    
    public static class ReportData {
        public String id;
        public String reporter;
        public String reported;
        public String reason;
        public String status;
        public String server;
    }
    
    private final Map<UUID, ReportData> viewingReport = new ConcurrentHashMap<>();

    public ReportDialogService(BeaconLabsVelocityLink plugin) {
        this.plugin = plugin;
        this.ACTION_KEY = new NamespacedKey(plugin, "report_action");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            String action = in.readUTF();
            
            if (action.equals("OPEN_CREATE") || action.equals("REPORT")) {
                String target = in.readUTF();
                reportingTarget.put(player.getUniqueId(), target);
                Bukkit.getScheduler().runTask(plugin, () -> openReportCreateGui(player, target));
            } else if (action.equals("OPEN_REPORTS") || action.equals("REPORTS")) {
                int size = in.readInt();
                List<String> reports = new ArrayList<>();
                for (int i = 0; i < size; i++) {
                    int id = in.readInt();
                    String reporter = in.readUTF();
                    String reported = in.readUTF();
                    String reason = in.readUTF();
                    String status = in.readUTF();
                    String server = in.readUTF();
                    reports.add(id + ":" + reporter + ":" + reported + ":" + reason + ":" + status + ":" + server);
                }
                Bukkit.getScheduler().runTask(plugin, () -> openReportsGui(player, reports));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse report dialog message: " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return true;
    }

    private void openReportCreateGui(Player player, String target) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text("Report: " + target));
        
        ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        bgMeta.displayName(Component.empty());
        bg.setItemMeta(bgMeta);
        for (int i = 0; i < 27; i++) inv.setItem(i, bg);

        inv.setItem(10, createReasonItem(Material.DIAMOND_SWORD, "Hacking", "report_reason_hacking"));
        inv.setItem(11, createReasonItem(Material.PAPER, "Spam", "report_reason_spam"));
        inv.setItem(12, createReasonItem(Material.ENDER_PEARL, "Harassment", "report_reason_harassment"));
        inv.setItem(14, createReasonItem(Material.TNT, "Inappropriate Language", "report_reason_language"));
        inv.setItem(15, createReasonItem(Material.FLINT_AND_STEEL, "Griefing", "report_reason_griefing"));
        inv.setItem(16, createReasonItem(Material.BARRIER, "Abuse", "report_reason_abuse"));
        
        inv.setItem(26, createReasonItem(Material.RED_STAINED_GLASS_PANE, "Cancel", "report_cancel"));
        
        player.openInventory(inv);
    }
    
    private void openReportsGui(Player player, List<String> reports) {
        Inventory inv = Bukkit.createInventory(null, 54, Component.text("Active Reports"));
        
        ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        bgMeta.displayName(Component.empty());
        bg.setItemMeta(bgMeta);
        for (int i = 0; i < 54; i++) inv.setItem(i, bg);

        int slot = 0;
        for (String repStr : reports) {
            if (slot >= 45) break;
            
            String[] parts = repStr.split(":");
            if (parts.length >= 6) {
                String id = parts[0];
                String reporter = parts[1];
                String reported = parts[2];
                String reason = parts[3];
                String status = parts[4];
                String server = parts[5];
                
                ItemStack item = new ItemStack(Material.PAPER);
                ItemMeta meta = item.getItemMeta();
                meta.displayName(Component.text("Report #" + id, NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("Reporter: ", NamedTextColor.GRAY).append(Component.text(reporter, NamedTextColor.YELLOW)).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Reported: ", NamedTextColor.GRAY).append(Component.text(reported, NamedTextColor.RED)).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Server: ", NamedTextColor.GRAY).append(Component.text(server, NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Reason: ", NamedTextColor.GRAY).append(Component.text(reason, NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Status: ", NamedTextColor.GRAY).append(Component.text(status, NamedTextColor.GREEN)).decoration(TextDecoration.ITALIC, false));
                meta.lore(lore);
                meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "view_report_" + id + ":" + reporter + ":" + reported + ":" + reason + ":" + status + ":" + server);
                item.setItemMeta(meta);
                
                inv.setItem(slot++, item);
            }
        }

        inv.setItem(49, createReasonItem(Material.BARRIER, "Close", "report_close"));
        
        player.openInventory(inv);
        
    }

    private void openReportActionGui(Player player, ReportData data) {
        Inventory inv = Bukkit.createInventory(null, 36, Component.text("Report #" + data.id + " Actions"));
        ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        bgMeta.displayName(Component.empty());
        bg.setItemMeta(bgMeta);
        for (int i = 0; i < 36; i++) inv.setItem(i, bg);

        // Player Head at slot 4
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) head.getItemMeta();
        skullMeta.displayName(Component.text("Report Details", NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        org.bukkit.profile.PlayerProfile profile = Bukkit.createProfile(data.reported);
        skullMeta.setOwnerProfile(profile);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Reporter: ", NamedTextColor.GRAY).append(Component.text(data.reporter, NamedTextColor.YELLOW)).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Reported: ", NamedTextColor.GRAY).append(Component.text(data.reported, NamedTextColor.RED)).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Server: ", NamedTextColor.GRAY).append(Component.text(data.server, NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Reason: ", NamedTextColor.GRAY).append(Component.text(data.reason, NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Status: ", NamedTextColor.GRAY).append(Component.text(data.status, NamedTextColor.GREEN)).decoration(TextDecoration.ITALIC, false));
        skullMeta.lore(lore);
        head.setItemMeta(skullMeta);
        inv.setItem(4, head);

        inv.setItem(19, createReasonItem(Material.YELLOW_DYE, "Mark In Progress", "report_status_" + data.id + "_IN_PROGRESS"));
        inv.setItem(21, createReasonItem(Material.LIME_DYE, "Resolve", "report_resolve_menu"));
        inv.setItem(23, createReasonItem(Material.GRAY_DYE, "Reject", "report_reject_menu"));
        
        inv.setItem(25, createReasonItem(Material.WRITABLE_BOOK, "Chat Report", "report_chatreport_" + data.reported));
        
        if (!data.server.equalsIgnoreCase("Unknown")) {
            inv.setItem(31, createReasonItem(Material.COMPASS, "Jump to Server", "report_jump_" + data.server));
        }
        
        inv.setItem(35, createReasonItem(Material.SPECTRAL_ARROW, "Back to List", "report_back_to_list"));

        player.openInventory(inv);
    }

    private void openReportResolveGui(Player player, ReportData data) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text("Resolve Report #" + data.id));
        ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        bgMeta.displayName(Component.empty());
        bg.setItemMeta(bgMeta);
        for (int i = 0; i < 27; i++) inv.setItem(i, bg);

        inv.setItem(11, createReasonItem(Material.IRON_SWORD, "Player Punished", "report_status_" + data.id + "_RESOLVED_Player Punished"));
        inv.setItem(13, createReasonItem(Material.PAPER, "Handled", "report_status_" + data.id + "_RESOLVED_Handled"));
        inv.setItem(15, createReasonItem(Material.BARRIER, "False Alarm", "report_status_" + data.id + "_RESOLVED_False Alarm"));
        
        inv.setItem(26, createReasonItem(Material.SPECTRAL_ARROW, "Back", "view_report_back"));

        player.openInventory(inv);
    }

    private void openReportRejectGui(Player player, ReportData data) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text("Reject Report #" + data.id));
        ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        bgMeta.displayName(Component.empty());
        bg.setItemMeta(bgMeta);
        for (int i = 0; i < 27; i++) inv.setItem(i, bg);

        inv.setItem(11, createReasonItem(Material.RED_DYE, "Confirm Reject", "report_status_" + data.id + "_REJECTED"));
        inv.setItem(15, createReasonItem(Material.BARRIER, "Cancel", "report_close"));
        
        inv.setItem(26, createReasonItem(Material.SPECTRAL_ARROW, "Back", "view_report_back"));

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
        if (title.contains("Report: ") || title.contains("Active Reports") || title.contains("Report #") || title.contains("Resolve Report #") || title.contains("Reject Report #")) {
            event.setCancelled(true);
            
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;
            
            String action = clicked.getItemMeta().getPersistentDataContainer().get(ACTION_KEY, PersistentDataType.STRING);
            if (action == null) return;
            
            if (action.equals("report_cancel") || action.equals("report_close")) {
                SoundUtil.playSound(player, org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.closeInventory();
                return;
            }
            
            if (action.equals("report_back_to_list")) {
                SoundUtil.playSound(player, org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                sendToProxyCommand(player, "reports");
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
                    SoundUtil.playSound(player, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                    // Send command to Velocity
                    sendToProxyCommand(player, "report " + target + " " + reason);
                } else {
                    SoundUtil.playSound(player, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
                player.closeInventory();
                return;
            }

            if (action.startsWith("view_report_")) {
                SoundUtil.playSound(player, org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                if (action.equals("view_report_back")) {
                    ReportData data = viewingReport.get(player.getUniqueId());
                    if (data != null) {
                        openReportActionGui(player, data);
                    } else {
                        player.closeInventory();
                    }
                    return;
                }
                
                String[] parts = action.substring("view_report_".length()).split(":");
                if (parts.length >= 6) {
                    ReportData data = new ReportData();
                    data.id = parts[0];
                    data.reporter = parts[1];
                    data.reported = parts[2];
                    data.reason = parts[3];
                    data.status = parts[4];
                    data.server = parts[5];
                    viewingReport.put(player.getUniqueId(), data);
                    openReportActionGui(player, data);
                }
                return;
            }
            if (action.startsWith("report_status_")) {
                String[] parts = action.substring("report_status_".length()).split("_", 2);
                String id = parts[0];
                String statusStr = parts[1]; // e.g. "IN_PROGRESS", "RESOLVED_Player Punished", "REJECTED"
                if (statusStr.startsWith("RESOLVED_")) {
                    statusStr = statusStr.replaceFirst("_", " ");
                }
                SoundUtil.playSound(player, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                sendToProxyCommand(player, "reports status " + id + " " + statusStr);
                player.closeInventory();
                return;
            }
            if (action.equals("report_resolve_menu")) {
                ReportData data = viewingReport.get(player.getUniqueId());
                if (data != null) {
                    SoundUtil.playSound(player, org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                    openReportResolveGui(player, data);
                }
                return;
            }
            if (action.equals("report_reject_menu")) {
                ReportData data = viewingReport.get(player.getUniqueId());
                if (data != null) {
                    SoundUtil.playSound(player, org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                    openReportRejectGui(player, data);
                }
                return;
            }
            if (action.startsWith("report_jump_")) {
                String server = action.substring("report_jump_".length());
                SoundUtil.playSound(player, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                sendToProxyCommand(player, "server " + server);
                player.closeInventory();
                return;
            }
            if (action.startsWith("report_chatreport_")) {
                String reported = action.substring("report_chatreport_".length());
                SoundUtil.playSound(player, org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                sendToProxyCommand(player, "chatreport " + reported);
                player.closeInventory();
                return;
            }
        }
    }
    
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        reportingTarget.remove(event.getPlayer().getUniqueId());
        // Don't remove viewingReport on close, because they might be navigating between GUIs.
        // It's just a small cache, it will be overwritten next time they view a report.
    }
    
    private void sendToProxyCommand(Player player, String command) {
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream data = new java.io.DataOutputStream(out);
            data.writeUTF(player.getUniqueId().toString());
            data.writeUTF(command);
            player.sendPluginMessage(plugin, "beaconlabs:proxy_command", out.toByteArray());
        } catch (Exception e) {}
    }
}
