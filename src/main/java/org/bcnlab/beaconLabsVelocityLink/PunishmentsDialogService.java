package org.bcnlab.beaconLabsVelocityLink;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.messaging.PluginMessageListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bcnlab.beaconLabsVelocityLink.utils.DurationUtils;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.text.SimpleDateFormat;

public class PunishmentsDialogService implements PluginMessageListener, Listener {
    public static final String CHANNEL = "beaconlabs:punishments_dialog";
    private final BeaconLabsVelocityLink plugin;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static class PunishmentRecord {
        boolean active;
        String type;
        String reason;
        String issuerName;
        long startTime;
        long endTime;
        long duration;
    }

    public PunishmentsDialogService(BeaconLabsVelocityLink plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            String targetName = in.readUTF();
            int size = in.readInt();
            
            List<PunishmentRecord> history = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                PunishmentRecord record = new PunishmentRecord();
                record.active = in.readBoolean();
                record.type = in.readUTF();
                record.reason = in.readUTF();
                record.issuerName = in.readUTF();
                record.startTime = in.readLong();
                record.endTime = in.readLong();
                record.duration = in.readLong();
                history.add(record);
            }

            Bukkit.getScheduler().runTask(plugin, () -> openPunishmentsGui(player, targetName, history));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse punishments dialog: " + e.getMessage());
        }
    }

    private void openPunishmentsGui(Player player, String targetName, List<PunishmentRecord> history) {
        Inventory inv = GuiHolder.create("punishments", 54, Component.text(targetName + "'s Punishments"));

        ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        bgMeta.displayName(Component.empty());
        bg.setItemMeta(bgMeta);
        for (int i = 0; i < 54; i++) inv.setItem(i, bg);
        
        if (history.isEmpty()) {
            ItemStack empty = new ItemStack(Material.PAPER);
            ItemMeta emptyMeta = empty.getItemMeta();
            emptyMeta.displayName(Component.text("No punishments found.", NamedTextColor.GREEN, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
            empty.setItemMeta(emptyMeta);
            inv.setItem(22, empty);
        } else {
            int slot = 0;
            for (PunishmentRecord record : history) {
                if (slot >= 45) break;
                
                Material icon = Material.PAPER;
                if (record.type.equalsIgnoreCase("BAN")) icon = Material.REDSTONE_BLOCK;
                else if (record.type.equalsIgnoreCase("MUTE")) icon = Material.NOTE_BLOCK;
                else if (record.type.equalsIgnoreCase("KICK")) icon = Material.IRON_DOOR;
                else if (record.type.equalsIgnoreCase("WARN")) icon = Material.YELLOW_DYE;
                
                ItemStack item = new ItemStack(icon);
                ItemMeta meta = item.getItemMeta();
                
                String activeStr = record.active ? "Active" : "Inactive";
                NamedTextColor activeColor = record.active ? NamedTextColor.GREEN : NamedTextColor.RED;
                meta.displayName(Component.text(record.type + " ", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .append(Component.text(activeStr, activeColor, TextDecoration.BOLD)).decoration(TextDecoration.ITALIC, false));
                
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("Reason: ", NamedTextColor.GRAY).append(Component.text(record.reason, NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Issuer: ", NamedTextColor.GRAY).append(Component.text(record.issuerName, NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Date: ", NamedTextColor.GRAY).append(Component.text(dateFormat.format(new Date(record.startTime)), NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false));
                
                String durationStr = record.duration > 0 ? org.bcnlab.beaconLabsVelocityLink.utils.DurationUtils.formatDuration(record.duration) : "Permanent";
                lore.add(Component.text("Duration: ", NamedTextColor.GRAY).append(Component.text(durationStr, NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false));
                
                if (record.duration > 0 && record.endTime > 0) {
                    lore.add(Component.text("Expires: ", NamedTextColor.GRAY).append(Component.text(dateFormat.format(new Date(record.endTime)), NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false));
                }
                
                meta.lore(lore);
                item.setItemMeta(meta);
                
                inv.setItem(slot++, item);
            }
        }
        
        inv.setItem(49, createCloseOrBackItem(targetName));

        player.openInventory(inv);
    }
    
    private ItemStack createCloseOrBackItem(String targetName) {
        if (targetName != null && !targetName.isEmpty()) {
            ItemStack item = new ItemStack(Material.SPECTRAL_ARROW);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text("Back to Info", NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
            meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "ig_back"), org.bukkit.persistence.PersistentDataType.STRING, targetName);
            item.setItemMeta(meta);
            return item;
        } else {
            ItemStack item = new ItemStack(Material.BARRIER);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text("Close", NamedTextColor.RED, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
            return item;
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof GuiHolder holder
                && "punishments".equals(holder.getId())) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;
            
            if (clicked.getType() == Material.BARRIER) {
                event.getWhoClicked().closeInventory();
            } else if (clicked.getType() == Material.SPECTRAL_ARROW) {
                String targetName = clicked.getItemMeta().getPersistentDataContainer().get(new org.bukkit.NamespacedKey(plugin, "ig_back"), org.bukkit.persistence.PersistentDataType.STRING);
                if (targetName != null) {
                    Player p = (Player) event.getWhoClicked();
                    try {
                        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                        java.io.DataOutputStream out = new java.io.DataOutputStream(b);
                        out.writeUTF(p.getUniqueId().toString());
                        out.writeUTF("ig " + targetName);
                        p.sendPluginMessage(plugin, "beaconlabs:proxy_command", b.toByteArray());
                    } catch (Exception e) {}
                }
            }
        }
    }
}
