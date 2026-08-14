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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.text.SimpleDateFormat;

public class IpInfoDialogService implements PluginMessageListener, Listener {
    public static final String CHANNEL = "beaconlabs:ipinfo_dialog";
    private final BeaconLabsVelocityLink plugin;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static class KnownAccount {
        String name;
        boolean online;
        long lastSeen;
    }

    public IpInfoDialogService(BeaconLabsVelocityLink plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            String ip = in.readUTF();
            String targetName = in.readUTF();
            int confidenceScore = in.readInt();
            String usageType = in.readUTF();
            String isp = in.readUTF();
            String domain = in.readUTF();
            String countryName = in.readUTF();
            String countryCode = in.readUTF();
            boolean isTor = in.readBoolean();
            int totalReports = in.readInt();
            String lastReportedAt = in.readUTF();
            boolean whitelisted = in.readBoolean();
            boolean blacklisted = in.readBoolean();
            String action = in.readUTF();

            int accountsSize = in.readInt();
            List<KnownAccount> accounts = new ArrayList<>();
            for (int i = 0; i < accountsSize; i++) {
                KnownAccount acc = new KnownAccount();
                acc.name = in.readUTF();
                acc.online = in.readBoolean();
                acc.lastSeen = in.readLong();
                accounts.add(acc);
            }

            Bukkit.getScheduler().runTask(plugin, () -> openIpInfoGui(player, ip, targetName, confidenceScore, usageType, isp, domain, countryName, countryCode, isTor, totalReports, lastReportedAt, whitelisted, blacklisted, action, accounts));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse ipinfo dialog: " + e.getMessage());
        }
    }

    private void openIpInfoGui(Player player, String ip, String targetName, int confidenceScore, String usageType, String isp, String domain, String countryName, String countryCode, boolean isTor, int totalReports, String lastReportedAt, boolean whitelisted, boolean blacklisted, String action, List<KnownAccount> accounts) {
        Inventory inv = GuiHolder.create("ipinfo", 54, Component.text("IP Info: " + ip));

        ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        bgMeta.displayName(Component.empty());
        bg.setItemMeta(bgMeta);
        for (int i = 0; i < 54; i++) inv.setItem(i, bg);

        // Header / Database status
        ItemStack header = new ItemStack(Material.PAPER);
        ItemMeta headerMeta = header.getItemMeta();
        headerMeta.displayName(Component.text("Abuse DB Status", NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> headerLore = new ArrayList<>();
        
        NamedTextColor scoreColor = confidenceScore >= 90 ? NamedTextColor.RED : (confidenceScore > 0 ? NamedTextColor.GOLD : NamedTextColor.GREEN);
        headerLore.add(Component.text("Confidence Score: ", NamedTextColor.GRAY).append(Component.text(confidenceScore + "%", scoreColor)).decoration(TextDecoration.ITALIC, false));
        headerLore.add(Component.text("Usage Type: ", NamedTextColor.GRAY).append(Component.text(usageType, NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false));
        headerLore.add(Component.text("ISP: ", NamedTextColor.GRAY).append(Component.text(isp, NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false));
        headerLore.add(Component.text("Domain: ", NamedTextColor.GRAY).append(Component.text(domain, NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false));
        
        String country = countryName.isEmpty() ? "Unknown" : countryName;
        if (!countryCode.isEmpty()) country += " (" + countryCode + ")";
        headerLore.add(Component.text("Country: ", NamedTextColor.GRAY).append(Component.text(country, NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false));
        
        headerLore.add(Component.text("Is Tor: ", NamedTextColor.GRAY).append(Component.text(isTor ? "Yes" : "No", isTor ? NamedTextColor.RED : NamedTextColor.GREEN)).decoration(TextDecoration.ITALIC, false));
        headerLore.add(Component.text("Total Reports: ", NamedTextColor.GRAY).append(Component.text(totalReports, NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false));
        if (!lastReportedAt.isEmpty()) {
            headerLore.add(Component.text("Last Reported: ", NamedTextColor.GRAY).append(Component.text(lastReportedAt, NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false));
        }
        
        headerMeta.lore(headerLore);
        header.setItemMeta(headerMeta);
        inv.setItem(4, header);
        
        // Local Overrides
        ItemStack overrides = new ItemStack(Material.REDSTONE_TORCH);
        ItemMeta overridesMeta = overrides.getItemMeta();
        overridesMeta.displayName(Component.text("Local Overrides", NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> overridesLore = new ArrayList<>();
        overridesLore.add(Component.text("Whitelisted: ", NamedTextColor.GRAY).append(Component.text(whitelisted ? "Yes" : "No", whitelisted ? NamedTextColor.GREEN : NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false));
        overridesLore.add(Component.text("Blacklisted: ", NamedTextColor.GRAY).append(Component.text(blacklisted ? "Yes" : "No", blacklisted ? NamedTextColor.RED : NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false));
        
        NamedTextColor actionColor = NamedTextColor.GREEN;
        if (action.equals("BLOCKED")) actionColor = NamedTextColor.RED;
        else if (action.equals("SCREENED")) actionColor = NamedTextColor.GOLD;
        overridesLore.add(Component.text("Action Taken: ", NamedTextColor.GRAY).append(Component.text(action, actionColor)).decoration(TextDecoration.ITALIC, false));
        
        overridesMeta.lore(overridesLore);
        overrides.setItemMeta(overridesMeta);
        inv.setItem(22, overrides);

        // Accounts list
        int slot = 27; // Start on row 4
        for (KnownAccount acc : accounts) {
            if (slot >= 53) break;
            
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            org.bukkit.inventory.meta.SkullMeta skullMeta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
            org.bukkit.profile.PlayerProfile profile = Bukkit.createProfile(acc.name);
            skullMeta.setOwnerProfile(profile);
            
            skullMeta.displayName(Component.text(acc.name, acc.online ? NamedTextColor.GREEN : NamedTextColor.WHITE, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
            List<Component> accLore = new ArrayList<>();
            if (acc.online) {
                accLore.add(Component.text("Status: ONLINE", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            } else {
                accLore.add(Component.text("Status: OFFLINE", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
                accLore.add(Component.text("Last Seen: ", NamedTextColor.GRAY).append(Component.text(dateFormat.format(new Date(acc.lastSeen)), NamedTextColor.YELLOW)).decoration(TextDecoration.ITALIC, false));
            }
            skullMeta.lore(accLore);
            head.setItemMeta(skullMeta);
            
            inv.setItem(slot++, head);
        }

        inv.setItem(53, createCloseOrBackItem(targetName));

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
                && "ipinfo".equals(holder.getId())) {
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
