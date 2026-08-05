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
            String nickname = in.readUTF();
            String skinSource = in.readUTF();
            boolean vanished = in.readBoolean();

            UUID uuid = UUID.fromString(uuidStr);
            Player target = Bukkit.getPlayer(uuid);
            if (target == null || !target.isOnline()) return;

            String effectiveNick = (nickname == null || nickname.isBlank()) ? null : nickname;
            String effectiveSkin = (skinSource == null || skinSource.isBlank()) ? null : skinSource;

            // Notice: For cross-proxy nick sync, the payload would need to include the rank. 
            // For now, if called via plugin message, rank isn't in payload, so it keeps current or clears.
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

    public void toggleVanish(Player player) {
        boolean current = Boolean.TRUE.equals(vanishedPlayers.getOrDefault(player.getUniqueId(), false));
        applyVanishState(player, !current);
    }

    private void applyState(Player player, String nickname, String skinSource, boolean vanished) {
        if (player == null || !player.isOnline()) return;

        vanishedPlayers.put(player.getUniqueId(), vanished);
        if (nickname != null && !nickname.isBlank()) {
            nickedPlayers.put(player.getUniqueId(), nickname);
        } else {
            nickedPlayers.remove(player.getUniqueId());
        }
        applyNameState(player, nickname);
        applySkinState(player, nickname, skinSource);
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
                String prefix = group.getCachedData().getMetaData(net.luckperms.api.query.QueryOptions.defaultContextualOptions()).getPrefix();
                return prefix == null ? "" : prefix;
            }
        } catch (Exception e) {}
        return "";
    }

    private void applyNameState(Player player, String nickname) {
        String name = (nickname == null || nickname.isBlank()) ? player.getName() : nickname;
        player.setDisplayName(name);
        player.playerListName(net.kyori.adventure.text.Component.text(name));
        cleanupTeams(player);
        player.customName(name.equals(player.getName()) ? null : net.kyori.adventure.text.Component.text(name));
        player.setCustomNameVisible(!name.equals(player.getName()));

        try {
            com.destroystokyo.paper.profile.PlayerProfile profile = player.getPlayerProfile();
            profile.setName(name);
            player.setPlayerProfile(profile);
        } catch (Exception e) {
            log.warning("[VS] Failed to set profile name for " + player.getName());
        }
        
        // Update TAB plugin formatting
        if (nickname != null && !nickname.isBlank()) {
            String fakeRank = getFakeRank(player);
            if (fakeRank == null || fakeRank.isBlank()) fakeRank = "default";
            
            // Get prefix from LuckPerms for the fake rank
            String fakePrefix = getLuckPermsPrefix(fakeRank);
            
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tab player " + player.getName() + " customprefix \"" + fakePrefix + "\"");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tab player " + player.getName() + " customtagname \"" + fakePrefix + name + "\"");
        } else {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tab player " + player.getName() + " customprefix remove");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tab player " + player.getName() + " customtagname remove");
        }
        
        // Force NametagGenerator to update the underlying scoreboard teams
        org.bukkit.plugin.Plugin corePlugin = Bukkit.getPluginManager().getPlugin("BeaconLabsCore");
        if (corePlugin != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    Object generator = corePlugin.getClass().getDeclaredField("nametagGenerator").get(corePlugin);
                    if (generator != null) {
                        generator.getClass().getMethod("updatePlayerNametag", Player.class).invoke(generator, player);
                    }
                } catch (Exception e) {
                    // Ignore if missing
                }
            });
        }
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
