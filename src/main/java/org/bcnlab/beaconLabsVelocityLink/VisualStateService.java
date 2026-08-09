package org.bcnlab.beaconLabsVelocityLink;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public final class VisualStateService implements PluginMessageListener, Listener {

    public static final String CHANNEL = "beaconlabs:visual_state";

    private final BeaconLabsVelocityLink plugin;
    private final Logger log;
    private final Map<UUID, PlayerProfile> originalProfiles = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> vanishedPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, String> nickedPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, String> nickedRanks = new ConcurrentHashMap<>();
    
    private static class PreloadedNick {
        String nickname;
        String skinSource;
        long timestamp;
        PreloadedNick(String n, String s, long t) { nickname = n; skinSource = s; timestamp = t; }
    }
    private final Map<UUID, PreloadedNick> preloadedNicks = new ConcurrentHashMap<>();

    public boolean isNicked(Player player) {
        return nickedPlayers.containsKey(player.getUniqueId());
    }

    public String getFakeRank(Player player) {
        return nickedRanks.get(player.getUniqueId());
    }

    public String getNickname(Player player) {
        return nickedPlayers.get(player.getUniqueId());
    }

    public VisualStateService(BeaconLabsVelocityLink plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        originalProfiles.put(player.getUniqueId(), (PlayerProfile) player.getPlayerProfile().clone());
        
        PreloadedNick preload = preloadedNicks.remove(player.getUniqueId());
        if (preload != null && System.currentTimeMillis() - preload.timestamp < 10000) {
            applyState(player, preload.nickname, preload.skinSource, Boolean.TRUE.equals(vanishedPlayers.getOrDefault(player.getUniqueId(), false)));
        } else {
            // request state from proxy
            try {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                java.io.DataOutputStream data = new java.io.DataOutputStream(out);
                data.writeUTF(player.getUniqueId().toString());
                data.writeUTF("STATE_REQUEST");
                data.writeUTF("");
                data.writeUTF("");
                player.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
            } catch (Exception e) {}
        }
        
        Bukkit.getScheduler().runTask(plugin, () -> applyVanishVisibility(player));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        originalProfiles.remove(uuid);
        vanishedPlayers.remove(uuid);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            String uuidStr = in.readUTF();
            
            // We use the second field to differentiate between raw state updates and actions
            String field2 = in.readUTF();
            
            UUID uuid = UUID.fromString(uuidStr);
            
            if ("NICK_PRELOAD".equals(field2)) {
                String nickname = in.readUTF();
                String skinSource = in.readUTF();
                preloadedNicks.put(uuid, new PreloadedNick(nickname, skinSource, System.currentTimeMillis()));
                return;
            }
            
            Player target = Bukkit.getPlayer(uuid);
            if (target == null || !target.isOnline()) return;

            if ("FORCE_UNNICK".equals(field2)) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    applyNick(target, null, null);
                    target.sendMessage(plugin.getPrefix(target).append(net.kyori.adventure.text.Component.text("Your nickname was removed because a player with that name joined.", net.kyori.adventure.text.format.NamedTextColor.GRAY)));
                });
                return;
            } else if ("FORCE_RENICK".equals(field2)) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    // Random re-nick
                    String[] names = new String[]{"Alex", "Steve", "Herobrine", "Notch", "Technoblade", "Dream", "Xisuma", "Hypnotize", "AntVenom", "CaptainSparklez"};
                    String newNick = names[java.util.concurrent.ThreadLocalRandom.current().nextInt(names.length)];
                    applyNick(target, newNick, getFakeRank(target));
                    target.sendMessage(plugin.getPrefix(target).append(net.kyori.adventure.text.Component.text("Your nickname was changed to ", net.kyori.adventure.text.format.NamedTextColor.GRAY)).append(net.kyori.adventure.text.Component.text(newNick, net.kyori.adventure.text.format.NamedTextColor.GOLD)).append(net.kyori.adventure.text.Component.text(" because the real owner joined.", net.kyori.adventure.text.format.NamedTextColor.GRAY)));
                    
                    // Also notify proxy!
                    try {
                        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                        java.io.DataOutputStream data = new java.io.DataOutputStream(out);
                        data.writeUTF(target.getUniqueId().toString());
                        data.writeUTF("NICK");
                        data.writeUTF(newNick);
                        data.writeUTF(newNick);
                        String rank = getFakeRank(target);
                        data.writeUTF(rank != null ? rank : "");
                        target.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
                    } catch (Exception e) {}
                });
                return;
            } else if ("UNNICK".equals(field2)) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    applyNick(target, null, null);
                });
                return;
            } else if ("NICK".equals(field2)) {
                // Sent from proxy to apply nick state
                String nickname = in.readUTF();
                String skinSource = in.readUTF();
                Bukkit.getScheduler().runTask(plugin, () -> applyState(target, nickname, skinSource, Boolean.TRUE.equals(vanishedPlayers.getOrDefault(target.getUniqueId(), false))));
                return;
            } else if ("NICK_ACCEPTED".equals(field2)) {
                String nickname = in.readUTF();
                String skinSource = in.readUTF();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    applyNick(target, nickname, skinSource.isEmpty() ? null : skinSource);
                    target.sendMessage(plugin.getPrefix(target).append(net.kyori.adventure.text.Component.text("Nickname set to ", net.kyori.adventure.text.format.NamedTextColor.GRAY)).append(net.kyori.adventure.text.Component.text(nickname, net.kyori.adventure.text.format.NamedTextColor.GOLD)));
                });
                return;
            } else if ("NICK_DENIED".equals(field2)) {
                String nickname = in.readUTF();
                String skinSource = in.readUTF();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    target.sendMessage(plugin.getPrefix(target).append(net.kyori.adventure.text.Component.text("You cannot nick as a player that is already online.", net.kyori.adventure.text.format.NamedTextColor.RED)));
                });
                return;
            }

            // Legacy format or standard visual state broadcast
            String nickname = field2;
            String skinSource = in.readUTF();
            boolean vanished = in.readBoolean();

            String effectiveNick = (nickname == null || nickname.isBlank()) ? null : nickname;
            String effectiveSkin = (skinSource == null || skinSource.isBlank()) ? null : skinSource;

            Bukkit.getScheduler().runTask(plugin, () ->
                applyState(target, effectiveNick, effectiveSkin, vanished));
        } catch (Exception e) {
            log.warning("[VS] Failed to decode visual state: " + e.getMessage());
        }
    }

    public void applyNick(Player player, String nickname, String fakeRank) {
        if (nickname != null && !nickname.isBlank()) {
            nickedPlayers.put(player.getUniqueId(), nickname);
            if (fakeRank != null && !fakeRank.isBlank()) {
                nickedRanks.put(player.getUniqueId(), fakeRank);
            } else {
                nickedRanks.remove(player.getUniqueId());
            }
        } else {
            nickedPlayers.remove(player.getUniqueId());
            nickedRanks.remove(player.getUniqueId());
        }
        applyNameState(player, nickname);
        applySkinState(player, nickname, nickname);
    }

    public boolean toggleVanish(Player player) {
        boolean current = Boolean.TRUE.equals(vanishedPlayers.getOrDefault(player.getUniqueId(), false));
        applyVanishState(player, !current);
        return !current;
    }

    public String getOriginalName(Player player) {
        com.destroystokyo.paper.profile.PlayerProfile original = originalProfiles.get(player.getUniqueId());
        return original != null ? original.getName() : player.getName();
    }

    private void applyState(Player player, String nickname, String skinSource, boolean vanished) {
        if (player == null || !player.isOnline()) return;

        vanishedPlayers.put(player.getUniqueId(), vanished);
        if (nickname != null && !nickname.isBlank()) {
            nickedPlayers.put(player.getUniqueId(), nickname);
            if (skinSource != null && !skinSource.isBlank()) {
                nickedRanks.put(player.getUniqueId(), skinSource);
            } else {
                nickedRanks.remove(player.getUniqueId());
            }
        } else {
            nickedPlayers.remove(player.getUniqueId());
            nickedRanks.remove(player.getUniqueId());
        }
        applyNameState(player, nickname);
        applySkinState(player, nickname, nickname);
        applyVanishState(player, vanished);
    }

    private String getLuckPermsPrefix(String fakeRank) {
        if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            return "";
        }
        try {
            return getLuckPermsPrefixInternal(fakeRank);
        } catch (Throwable t) {
            return "";
        }
    }

    private String getLuckPermsPrefixInternal(String fakeRank) {
        try {
            net.luckperms.api.LuckPerms luckPerms = net.luckperms.api.LuckPermsProvider.get();
            net.luckperms.api.model.group.Group group = luckPerms.getGroupManager().getGroup(fakeRank.toLowerCase());
            if (group != null) {
                String prefix = group.getNodes(net.luckperms.api.node.NodeType.PREFIX).stream().map(net.luckperms.api.node.types.PrefixNode::getMetaValue).findFirst().orElse(null);
                return prefix == null ? "" : prefix;
            }
        } catch (Exception e) {}
        return "";
    }

    private void applyNameState(Player player, String nickname) {
        String originalName = getOriginalName(player);
        String name = (nickname == null || nickname.isBlank()) ? originalName : nickname;
        player.setDisplayName(name);
        player.playerListName(net.kyori.adventure.text.Component.text(name));
        cleanupTeams(player);
        player.customName(name.equals(originalName) ? null : net.kyori.adventure.text.Component.text(name));
        player.setCustomNameVisible(!name.equals(originalName));

        try {
            if (plugin.getProtocolVersion(player.getUniqueId()) > 47) {
                com.destroystokyo.paper.profile.PlayerProfile profile = player.getPlayerProfile();
                profile.setName(name);
                player.setPlayerProfile(profile);
            }
        } catch (Exception e) {
            log.warning("[VS] Failed to set profile name for " + originalName);
        }
        
        // Update TAB plugin formatting via API (with small delay to let profile changes propagate)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (nickname != null && !nickname.isBlank()) {
                String fakeRank = getFakeRank(player);
                if (fakeRank == null || fakeRank.isBlank()) fakeRank = "default";
                
                // Get prefix from LuckPerms for the fake rank
                String fakePrefix = getLuckPermsPrefix(fakeRank);
                updateTabPlugin(player, fakePrefix, name);
            } else {
                updateTabPlugin(player, null, null);
            }
        }, 5L);
        
        // Force NametagGenerator to update the underlying scoreboard teams
        org.bukkit.plugin.Plugin corePlugin = Bukkit.getPluginManager().getPlugin("BeaconLabsCore");
        if (corePlugin != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                try {
                    Object generator = corePlugin.getClass().getMethod("getNametagGenerator").invoke(corePlugin);
                    if (generator != null) {
                        generator.getClass().getMethod("updatePlayerNametag", Player.class).invoke(generator, player);
                    }
                } catch (Exception e) {
                    // Ignore if missing
                }
            }, 10L);
        }
        
        // When unnicking, explicitly refresh viewers so nametag above head updates
        if (nickname == null || nickname.isBlank()) {
            if (!Boolean.TRUE.equals(vanishedPlayers.get(player.getUniqueId()))) {
                refreshViewersDelayed(player);
            }
        }
    }
    
    private void updateTabPlugin(Player player, String prefix, String name) {
        if (!Bukkit.getPluginManager().isPluginEnabled("TAB")) {
            return;
        }
        try {
            Class<?> tabApiClass = Class.forName("me.neznamy.tab.api.TabAPI");
            Object tabApi = tabApiClass.getMethod("getInstance").invoke(null);
            if (tabApi == null) return;
            
            Object tabPlayer = tabApiClass.getMethod("getPlayer", java.util.UUID.class).invoke(tabApi, player.getUniqueId());
            if (tabPlayer == null) return;
            
            Object tabListMgr = tabApiClass.getMethod("getTabListFormatManager").invoke(tabApi);
            Object nameTagMgr = tabApiClass.getMethod("getNameTagManager").invoke(tabApi);
            
            Object prefixObj = getTabComponent(prefix);
            Object nameObj = getTabComponent(name);

            if (tabListMgr != null) {
                invokeMethod(tabListMgr, "setPrefix", tabPlayer, prefixObj);
                invokeMethod(tabListMgr, "setName", tabPlayer, nameObj);
            }
            if (nameTagMgr != null) {
                invokeMethod(nameTagMgr, "setPrefix", tabPlayer, prefixObj);
            }
        } catch (Throwable t) {
            log.warning("[VS] Failed to update TAB plugin: " + t.getMessage());
        }
    }

    private Object getTabComponent(String text) {
        if (text == null) return null;
        try {
            Class<?> clazz = Class.forName("me.neznamy.tab.api.chat.TabComponent");
            return clazz.getMethod("optimized", String.class).invoke(null, text);
        } catch (Exception e1) {
            try {
                Class<?> clazz = Class.forName("me.neznamy.tab.shared.chat.TabComponent");
                return clazz.getMethod("optimized", String.class).invoke(null, text);
            } catch (Exception e2) {
                try {
                    Class<?> clazz = Class.forName("me.neznamy.tab.api.chat.IChatBaseComponent");
                    return clazz.getMethod("optimizedComponent", String.class).invoke(null, text);
                } catch (Exception e3) {
                    return text; // Fallback if it accepts String
                }
            }
        }
    }

    private void invokeMethod(Object manager, String methodName, Object tabPlayer, Object value) {
        try {
            for (java.lang.reflect.Method m : manager.getClass().getMethods()) {
                if (m.getName().equals(methodName) && m.getParameterCount() == 2) {
                    m.invoke(manager, tabPlayer, value);
                    return;
                }
            }
        } catch (Exception e) {}
    }

    private void cleanupTeams(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        String real = player.getName();
        for (Team t : board.getTeams()) {
            if (t.hasEntry(real)) t.removeEntry(real);
        }
    }

    private void applySkinState(Player player, String nickname, String skinSource) {
        if (nickname == null && skinSource == null) {
            PlayerProfile original = originalProfiles.get(player.getUniqueId());
            if (original != null) {
                applyPlayerProfile(player, original);
                if (!Boolean.TRUE.equals(vanishedPlayers.get(player.getUniqueId()))) {
                    refreshViewersDelayed(player);
                }
            }
            return;
        }

        String source = (skinSource != null && !skinSource.isBlank()) ? skinSource : nickname;
        if (source == null || source.isBlank()) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            PlayerProfile lookup = Bukkit.createProfile(source);
            lookup.complete(true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (!lookup.hasProperty("textures")) {
                    log.warning("[VS] Skin resolved but no textures for " + source);
                    return;
                }
                log.info("[VS] Applying skin for " + source + " to " + player.getName());
                boolean applied = applyPlayerProfile(player, lookup);
                log.info("[VS] Skin application result: " + applied);
                if (applied && !Boolean.TRUE.equals(vanishedPlayers.get(player.getUniqueId()))) {
                    refreshViewersDelayed(player);
                }
            });
        });
    }

    private void applyVanishState(Player player, boolean vanished) {
        vanishedPlayers.put(player.getUniqueId(), vanished);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getUniqueId().equals(player.getUniqueId())) continue;
            if (vanished) {
                viewer.hidePlayer(plugin, player);
            } else {
                viewer.showPlayer(plugin, player);
            }
        }
    }

    private void applyVanishVisibility(Player viewer) {
        for (Map.Entry<UUID, Boolean> entry : vanishedPlayers.entrySet()) {
            if (!Boolean.TRUE.equals(entry.getValue())) continue;
            Player vanished = Bukkit.getPlayer(entry.getKey());
            if (vanished != null && vanished.isOnline() && !vanished.getUniqueId().equals(viewer.getUniqueId())) {
                viewer.hidePlayer(plugin, vanished);
            }
        }
    }

    private void refreshViewersDelayed(Player player) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getUniqueId().equals(player.getUniqueId())) continue;
            viewer.hidePlayer(plugin, player);
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (viewer.getUniqueId().equals(player.getUniqueId())) continue;
                viewer.showPlayer(plugin, player);
            }
        });
    }

    private boolean applyPlayerProfile(Player player, PlayerProfile profile) {
        try {
            PlayerProfile current = (PlayerProfile) player.getPlayerProfile();
            current.setProperties(profile.getProperties());
            player.setPlayerProfile(current);
            log.info("[VS] applyPlayerProfile SUCCESS for " + player.getName());
            return true;
        } catch (Exception e) {
            log.warning("[VS] applyPlayerProfile failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            return false;
        }
    }
}
