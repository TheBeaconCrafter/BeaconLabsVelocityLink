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
    public static final String OPEN_CHANNEL = "beaconlabs:cloud_gui_open";
    public static final String ACTION_CHANNEL = "beaconlabs:cloud_gui_action";
    private final BeaconLabsVelocityLink plugin;

    public static final String SUBMENU_CHANNEL = "beaconlabs:cloud_gui_submenu";
    public static final String ADD_CHANNEL = "beaconlabs:cloud_server_add";
    private static final org.bukkit.NamespacedKey SERVER_ID_KEY = new org.bukkit.NamespacedKey("beaconlabs", "server_id");
    private final java.util.Map<java.util.UUID, java.util.List<ItemStack>> cachedServers = new java.util.concurrent.ConcurrentHashMap<>();

    public CloudDialogService(BeaconLabsVelocityLink plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (channel.equals(OPEN_CHANNEL)) {
            Inventory inv = Bukkit.createInventory(null, 54, Component.text("Cloud Servers").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            
            ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta glassMeta = glass.getItemMeta();
            glassMeta.displayName(Component.text(" "));
            glass.setItemMeta(glassMeta);
            for (int i = 0; i < 54; i++) {
                if (i < 9 || i > 44 || i % 9 == 0 || i % 9 == 8) {
                    inv.setItem(i, glass);
                }
            }
            

            
            Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(inv));
        } else if (channel.equals(ADD_CHANNEL)) {
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
                String id = in.readUTF();
                String name = in.readUTF();
                boolean running = in.readBoolean();
                boolean installing = in.readBoolean();
                
                ItemStack item = new ItemStack(Material.COMMAND_BLOCK);
                if (installing) {
                    item.setType(Material.GOLD_BLOCK);
                } else if (running) {
                    item.setType(Material.EMERALD_BLOCK);
                } else {
                    item.setType(Material.REDSTONE_BLOCK);
                }
                
                ItemMeta meta = item.getItemMeta();
                meta.displayName(Component.text(name, NamedTextColor.AQUA, net.kyori.adventure.text.format.TextDecoration.BOLD).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("ID: " + id, NamedTextColor.GRAY).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                if (installing) {
                    lore.add(Component.text("Status: Installing", NamedTextColor.YELLOW).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                } else if (running) {
                    lore.add(Component.text("Status: Online", NamedTextColor.GREEN).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                } else {
                    lore.add(Component.text("Status: Offline", NamedTextColor.RED).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                }
                lore.add(Component.empty());
                lore.add(Component.text("Click to manage", NamedTextColor.YELLOW).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                meta.lore(lore);
                meta.getPersistentDataContainer().set(SERVER_ID_KEY, org.bukkit.persistence.PersistentDataType.STRING, id);
                item.setItemMeta(meta);
                
                cachedServers.computeIfAbsent(player.getUniqueId(), k -> new java.util.ArrayList<>()).add(item);
                
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Inventory inv = player.getOpenInventory().getTopInventory();
                    boolean isOpen = inv != null && player.getOpenInventory().title() instanceof net.kyori.adventure.text.TextComponent && ((net.kyori.adventure.text.TextComponent) player.getOpenInventory().title()).content().equals("Cloud Servers");
                    if (isOpen) {
                        for (int i = 0; i < 54; i++) {
                            if (i < 9 || i > 44 || i % 9 == 0 || i % 9 == 8) continue;
                            if (inv.getItem(i) == null || inv.getItem(i).getType() == Material.AIR) {
                                inv.setItem(i, item);
                                break;
                            }
                        }
                    }
                });
            } catch (Exception e) {}
        } else if (channel.equals(SUBMENU_CHANNEL)) {
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
                String id = in.readUTF();
                String name = in.readUTF();
                boolean running = in.readBoolean();
                boolean installing = in.readBoolean();
                
                Inventory inv = Bukkit.createInventory(null, 27, Component.text("Manage: " + name).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                
                // Status item
                ItemStack statusItem = new ItemStack(running ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK);
                ItemMeta statusMeta = statusItem.getItemMeta();
                statusMeta.displayName(Component.text(running ? "Running" : "Offline", running ? NamedTextColor.GREEN : NamedTextColor.RED).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                List<Component> statusLore = new ArrayList<>();
                statusLore.add(Component.text("ID: " + id, NamedTextColor.GRAY).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                statusMeta.lore(statusLore);
                statusItem.setItemMeta(statusMeta);
                inv.setItem(4, statusItem);
                
                // Start button
                ItemStack startItem = new ItemStack(Material.LIME_DYE);
                ItemMeta startMeta = startItem.getItemMeta();
                startMeta.displayName(Component.text("Start Server", NamedTextColor.GREEN).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                startItem.setItemMeta(startMeta);
                inv.setItem(11, startItem);
                
                // Stop button
                ItemStack stopItem = new ItemStack(Material.RED_DYE);
                ItemMeta stopMeta = stopItem.getItemMeta();
                stopMeta.displayName(Component.text("Stop Server", NamedTextColor.RED).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                stopItem.setItemMeta(stopMeta);
                inv.setItem(13, stopItem);
                
                // Restart button
                ItemStack restartItem = new ItemStack(Material.YELLOW_DYE);
                ItemMeta restartMeta = restartItem.getItemMeta();
                restartMeta.displayName(Component.text("Restart Server", NamedTextColor.YELLOW).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                restartItem.setItemMeta(restartMeta);
                inv.setItem(15, restartItem);
                
                // Back button
                ItemStack backItem = new ItemStack(Material.ARROW);
                ItemMeta backMeta = backItem.getItemMeta();
                backMeta.displayName(Component.text("Back", NamedTextColor.GRAY).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                backItem.setItemMeta(backMeta);
                inv.setItem(18, backItem);
                
                Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(inv));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = ((net.kyori.adventure.text.TextComponent) event.getView().title()).content();
        if (title.equals("Cloud Servers")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null || event.getCurrentItem().getItemMeta() == null) return;
            
            Player player = (Player) event.getWhoClicked();
            List<Component> lore = event.getCurrentItem().getItemMeta().lore();
            if (lore == null || lore.isEmpty()) return;
            
            String idLine = ((net.kyori.adventure.text.TextComponent) lore.get(0)).content();
            String id = idLine.replace("ID: ", "");
            String name = ((net.kyori.adventure.text.TextComponent) event.getCurrentItem().getItemMeta().displayName()).content();
            
            sendAction(player, "INFO", id, name);
        } else if (title.startsWith("Manage: ")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null || event.getCurrentItem().getItemMeta() == null) return;
            
            Player player = (Player) event.getWhoClicked();
            ItemStack statusItem = event.getInventory().getItem(4);
            if (statusItem == null || statusItem.getItemMeta() == null || statusItem.getItemMeta().lore() == null) return;
            
            String idLine = ((net.kyori.adventure.text.TextComponent) statusItem.getItemMeta().lore().get(0)).content();
            String id = idLine.replace("ID: ", "");
            
            Material type = event.getCurrentItem().getType();
            if (type == Material.ARROW) {
                // Return to main menu - we can just run the cloud command
                try {
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    java.io.DataOutputStream data = new java.io.DataOutputStream(out);
                    data.writeUTF(player.getUniqueId().toString());
                    data.writeUTF("cloud");
                    player.sendPluginMessage(plugin, "beaconlabs:proxy_command", out.toByteArray());
                } catch (Exception e) {}
            } else if (type == Material.LIME_DYE) {
                sendAction(player, "START", id, null);
                player.closeInventory();
            } else if (type == Material.RED_DYE) {
                sendAction(player, "STOP", id, null);
                player.closeInventory();
            } else if (type == Material.YELLOW_DYE) {
                sendAction(player, "RESTART", id, null);
                player.closeInventory();
            }
        }
    }

    private void sendAction(Player player, String action, String serverId, String serverName) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DataOutputStream data = new DataOutputStream(out);
            data.writeUTF(player.getUniqueId().toString());
            data.writeUTF(action);
            data.writeUTF(serverId);
            if (serverName != null) {
                data.writeUTF(serverName);
            }
            player.sendPluginMessage(plugin, ACTION_CHANNEL, out.toByteArray());
        } catch (Exception e) {}
    }
}
