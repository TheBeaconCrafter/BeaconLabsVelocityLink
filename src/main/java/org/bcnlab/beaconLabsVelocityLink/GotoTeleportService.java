package org.bcnlab.beaconLabsVelocityLink;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GotoTeleportService implements PluginMessageListener, Listener {
    public static final String CHANNEL = "beaconlabs:goto_tp";
    private final BeaconLabsVelocityLink plugin;
    private final Map<UUID, UUID> pendingTeleports = new ConcurrentHashMap<>();

    public GotoTeleportService(BeaconLabsVelocityLink plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            UUID executor = UUID.fromString(in.readUTF());
            UUID target = UUID.fromString(in.readUTF());
            
            Player targetPlayer = Bukkit.getPlayer(target);
            Player executorPlayer = Bukkit.getPlayer(executor);
            
            if (executorPlayer != null && targetPlayer != null) {
                // Both players are already here, teleport immediately
                Bukkit.getScheduler().runTask(plugin, () -> executorPlayer.teleport(targetPlayer));
            } else if (targetPlayer != null) {
                // Executor is joining soon
                pendingTeleports.put(executor, target);
                
                // Expire after 10 seconds if they don't join
                Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                    pendingTeleports.remove(executor);
                }, 200L);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse goto_tp: " + e.getMessage());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID executor = event.getPlayer().getUniqueId();
        UUID target = pendingTeleports.remove(executor);
        if (target != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player targetPlayer = Bukkit.getPlayer(target);
                if (targetPlayer != null && event.getPlayer().isOnline()) {
                    event.getPlayer().teleport(targetPlayer);
                }
            }, 10L); // Small delay to let them spawn fully
        }
    }
}
