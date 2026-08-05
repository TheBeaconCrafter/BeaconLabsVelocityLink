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
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.messaging.PluginMessageListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FriendDialogService implements PluginMessageListener, Listener {
    public static final String CHANNEL = "beaconlabs:friend_dialog";
    private final BeaconLabsVelocityLink plugin;
    private final Map<UUID, List<FriendData>> playerFriendsCache = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerPageCache = new ConcurrentHashMap<>();

    private final Component title = Component.text("Your Friends").color(NamedTextColor.DARK_PURPLE);

    public FriendDialogService(BeaconLabsVelocityLink plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            int friendCount = in.readInt();
            List<FriendData> friends = new ArrayList<>();
            for (int i = 0; i < friendCount; i++) {
                String uuid = in.readUTF();
                String name = in.readUTF();
                boolean online = in.readBoolean();
                long friendsSince = in.readLong();
                long lastOnline = in.readLong();
                String server = in.readUTF();
                friends.add(new FriendData(UUID.fromString(uuid), name, online, friendsSince, lastOnline, server));
            }

            playerFriendsCache.put(player.getUniqueId(), friends);
            playerPageCache.put(player.getUniqueId(), 0);
            Bukkit.getScheduler().runTask(plugin, () -> showDialog(player, 0));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse friend dialog message: " + e.getMessage());
        }
    }

    private void showDialog(Player player, int page) {
        List<FriendData> friends = playerFriendsCache.getOrDefault(player.getUniqueId(), new ArrayList<>());
        
        int size = 54;
        Inventory gui = Bukkit.createInventory(null, size, title);
        
        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        if (borderMeta != null) {
            borderMeta.displayName(Component.text(" "));
            border.setItemMeta(borderMeta);
        }
        for (int i = 0; i < size; i++) {
            if (i < 9 || i >= size - 9 || i % 9 == 0 || i % 9 == 8) {
                gui.setItem(i, border);
            }
        }
        
        int itemsPerPage = 28; // 7x4
        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, friends.size());
        
        int slot = 10;
        for (int i = start; i < end; i++) {
            if (slot % 9 == 0 || slot % 9 == 8) slot++;
            if (slot >= size - 9) break;

            FriendData fd = friends.get(i);
            ItemStack item = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) item.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(fd.uuid));
                meta.displayName(Component.text(fd.name, 
                    fd.isOnline ? NamedTextColor.GREEN : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
                
                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());
                if (fd.isOnline) {
                    lore.add(Component.text("▪ Status: ", NamedTextColor.GRAY)
                            .append(Component.text("Online", NamedTextColor.GREEN))
                            .decoration(TextDecoration.ITALIC, false));
                    if (fd.server != null && !fd.server.isBlank()) {
                        lore.add(Component.text("  Server: ", NamedTextColor.DARK_GRAY)
                                .append(Component.text(fd.server, NamedTextColor.GOLD))
                                .decoration(TextDecoration.ITALIC, false));
                    }
                } else {
                    lore.add(Component.text("▪ Status: ", NamedTextColor.GRAY)
                            .append(Component.text("Offline", NamedTextColor.RED))
                            .decoration(TextDecoration.ITALIC, false));
                    if (fd.lastOnline > 0) {
                        String date = new java.text.SimpleDateFormat("MMM dd, yyyy").format(new java.util.Date(fd.lastOnline));
                        lore.add(Component.text("  Last seen: " + date, NamedTextColor.DARK_GRAY)
                                .decoration(TextDecoration.ITALIC, false));
                    }
                }
                
                if (fd.friendsSince > 0) {
                    lore.add(Component.empty());
                    String date = new java.text.SimpleDateFormat("MMM dd, yyyy").format(new java.util.Date(fd.friendsSince));
                    lore.add(Component.text("Friends since " + date, NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false));
                }
                lore.add(Component.empty());
                lore.add(Component.text("Left-Click to jump to server", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Right-Click to remove", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
                
                meta.lore(lore);
                item.setItemMeta(meta);
            }
            gui.setItem(slot++, item);
        }
        
        // Pagination
        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prev.getItemMeta();
            prevMeta.displayName(Component.text("Previous Page", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            prev.setItemMeta(prevMeta);
            gui.setItem(48, prev);
        }
        if (end < friends.size()) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = next.getItemMeta();
            nextMeta.displayName(Component.text("Next Page", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            next.setItemMeta(nextMeta);
            gui.setItem(50, next);
        }

        // Add friend item
        ItemStack add = new ItemStack(Material.OAK_SIGN);
        ItemMeta addMeta = add.getItemMeta();
        addMeta.displayName(Component.text("Add Friend", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        add.setItemMeta(addMeta);
        gui.setItem(49, add);
        
        player.openInventory(gui);
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().title().equals(title)) {
            event.setCancelled(true);
            
            if (event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.BLACK_STAINED_GLASS_PANE && event.getCurrentItem().getType() != Material.AIR) {
                if (event.getWhoClicked() instanceof Player player) {
                    int slot = event.getRawSlot();
                    int page = playerPageCache.getOrDefault(player.getUniqueId(), 0);
                    
                    if (slot == 48 && event.getCurrentItem().getType() == Material.ARROW) {
                        playerPageCache.put(player.getUniqueId(), page - 1);
                        showDialog(player, page - 1);
                    } else if (slot == 50 && event.getCurrentItem().getType() == Material.ARROW) {
                        playerPageCache.put(player.getUniqueId(), page + 1);
                        showDialog(player, page + 1);
                    } else if (slot == 49 && event.getCurrentItem().getType() == Material.OAK_SIGN) {
                        player.closeInventory();
                        
                        org.bukkit.Location loc = player.getLocation().clone();
                        org.bukkit.block.Block block = loc.getBlock();
                        
                        // Store original state
                        org.bukkit.block.data.BlockData oldData = block.getBlockData();
                        
                        block.setType(Material.OAK_SIGN, false);
                        org.bukkit.block.Sign sign = (org.bukkit.block.Sign) block.getState();
                        sign.line(0, Component.empty());
                        sign.line(1, Component.text("^^^^^^^^^^", NamedTextColor.GRAY));
                        sign.line(2, Component.text("Enter Friend", NamedTextColor.DARK_AQUA));
                        sign.line(3, Component.text("Name Above", NamedTextColor.DARK_AQUA));
                        sign.setWaxed(false);
                        sign.update(true, false);
                        
                        // Mark this sign as our custom Add Friend sign
                        sign.getPersistentDataContainer().set(
                            new org.bukkit.NamespacedKey(plugin, "add_friend_sign"),
                            org.bukkit.persistence.PersistentDataType.BYTE,
                            (byte) 1
                        );
                        sign.update();
                        
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            player.openSign(sign);
                        }, 2L);
                        
                        // Fallback restore block after 30 seconds
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (block.getType() == Material.OAK_SIGN && block.getState() instanceof org.bukkit.block.Sign checkSign) {
                                if (checkSign.getPersistentDataContainer().has(new org.bukkit.NamespacedKey(plugin, "add_friend_sign"), org.bukkit.persistence.PersistentDataType.BYTE)) {
                                    block.setBlockData(oldData);
                                }
                            }
                        }, 20L * 30L);
                        
                    } else if (event.getCurrentItem().getType() == Material.PLAYER_HEAD) {
                        SkullMeta meta = (SkullMeta) event.getCurrentItem().getItemMeta();
                        if (meta != null && meta.getOwningPlayer() != null) {
                            String name = meta.getOwningPlayer().getName();
                            if (name == null) name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(meta.displayName());
                            
                            if (event.isLeftClick()) {
                                player.closeInventory();
                                List<FriendData> friends = playerFriendsCache.getOrDefault(player.getUniqueId(), new ArrayList<>());
                                String targetServer = null;
                                for (FriendData fd : friends) {
                                    if (fd.uuid.equals(meta.getOwningPlayer().getUniqueId())) {
                                        targetServer = fd.server;
                                        break;
                                    }
                                }
                                if (targetServer != null && !targetServer.isBlank() && !targetServer.equals("Hidden")) {
                                    try {
                                        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                                        java.io.DataOutputStream out = new java.io.DataOutputStream(b);
                                        out.writeUTF("Connect");
                                        out.writeUTF(targetServer);
                                        player.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
                                    } catch (Exception e) {}
                                } else {
                                    player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Could not connect to this friend's server.</red>"));
                                }
                            } else if (event.isRightClick()) {
                                player.closeInventory();
                                try {
                                    java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                                    java.io.DataOutputStream out = new java.io.DataOutputStream(b);
                                    out.writeUTF(player.getUniqueId().toString());
                                    out.writeUTF("friend remove " + name);
                                    player.sendPluginMessage(plugin, "beaconlabs:proxy_command", b.toByteArray());
                                } catch (Exception e) {}
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onSignChange(org.bukkit.event.block.SignChangeEvent event) {
        org.bukkit.block.Sign sign = (org.bukkit.block.Sign) event.getBlock().getState();
        if (sign.getPersistentDataContainer().has(new org.bukkit.NamespacedKey(plugin, "add_friend_sign"), org.bukkit.persistence.PersistentDataType.BYTE)) {
            event.setCancelled(true);
            
            // Clean up the block
            event.getBlock().setType(Material.AIR);
            
            String friendName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.line(0)).trim();
            if (friendName.isEmpty()) {
                event.getPlayer().sendMessage(MiniMessage.miniMessage().deserialize("<red>You didn't enter a name.</red>"));
                return;
            }
            
            // Send proxy command to add friend
            try {
                java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                java.io.DataOutputStream out = new java.io.DataOutputStream(b);
                out.writeUTF(event.getPlayer().getUniqueId().toString());
                out.writeUTF("friend add " + friendName);
                event.getPlayer().sendPluginMessage(plugin, "beaconlabs:proxy_command", b.toByteArray());
                event.getPlayer().sendMessage(MiniMessage.miniMessage().deserialize("<green>Sent friend request to " + friendName + "!</green>"));
            } catch (Exception e) {}
        }
    }

    private static class FriendData {
        public final UUID uuid;
        public final String name;
        public final boolean isOnline;
        public final long friendsSince;
        public final long lastOnline;
        public final String server;

        public FriendData(UUID uuid, String name, boolean isOnline, long friendsSince, long lastOnline, String server) {
            this.uuid = uuid;
            this.name = name;
            this.isOnline = isOnline;
            this.friendsSince = friendsSince;
            this.lastOnline = lastOnline;
            this.server = server;
        }
    }
}
