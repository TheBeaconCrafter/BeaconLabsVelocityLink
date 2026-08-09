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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;

public class CloudDialogService implements PluginMessageListener, Listener {

    public static final String CHANNEL = "beaconlabs:cloud_gui";
    public static final String ACTION_CHANNEL = "beaconlabs:cloud_gui_action";
    private final BeaconLabsVelocityLink plugin;

    public CloudDialogService(BeaconLabsVelocityLink plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals(CHANNEL)) return;
        
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            int count = in.readInt();
            
            Inventory inv = Bukkit.createInventory(null, 54, Component.text("Cloud Servers"));
            
            for (int i = 0; i < count; i++) {
                String id = in.readUTF();
                String name = in.readUTF();
                
                ItemStack item = new ItemStack(Material.COMMAND_BLOCK);
                ItemMeta meta = item.getItemMeta();
                meta.displayName(Component.text(name, NamedTextColor.AQUA));
                
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("ID: " + id, NamedTextColor.GRAY));
                lore.add(Component.empty());
                lore.add(Component.text("Left-Click to Start", NamedTextColor.GREEN));
                lore.add(Component.text("Right-Click to Stop", NamedTextColor.RED));
                lore.add(Component.text("Shift-Click to Restart", NamedTextColor.YELLOW));
                
                meta.lore(lore);
                item.setItemMeta(meta);
                inv.addItem(item);
            }
            
            Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(inv));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().title().equals(Component.text("Cloud Servers"))) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null || event.getCurrentItem().getItemMeta() == null) return;
            
            Player player = (Player) event.getWhoClicked();
            List<Component> lore = event.getCurrentItem().getItemMeta().lore();
            if (lore == null || lore.isEmpty()) return;
            
            String idLine = ((net.kyori.adventure.text.TextComponent) lore.get(0)).content();
            String id = idLine.replace("ID: ", "");
            
            String action = null;
            if (event.isShiftClick()) {
                action = "RESTART";
            } else if (event.isRightClick()) {
                action = "STOP";
            } else if (event.isLeftClick()) {
                action = "START";
            }
            
            if (action != null) {
                sendAction(player, action, id);
                player.closeInventory();
            }
        }
    }

    private void sendAction(Player player, String action, String serverId) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DataOutputStream data = new DataOutputStream(out);
            data.writeUTF(player.getUniqueId().toString());
            data.writeUTF(action);
            data.writeUTF(serverId);
            player.sendPluginMessage(plugin, ACTION_CHANNEL, out.toByteArray());
        } catch (Exception e) {}
    }
}
