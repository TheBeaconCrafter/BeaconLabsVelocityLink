package org.bcnlab.beaconLabsVelocityLink;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ProtocolSyncService implements PluginMessageListener, Listener {
    public static final String CHANNEL = "beaconlabs:protocol_version";
    private static ProtocolSyncService instance;
    private final BeaconLabsVelocityLink plugin;
    private final Map<UUID, Integer> protocolVersions = new ConcurrentHashMap<>();

    public ProtocolSyncService(BeaconLabsVelocityLink plugin) {
        this.plugin = plugin;
        instance = this;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public static ProtocolSyncService getInstance() {
        return instance;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            UUID uuid = UUID.fromString(in.readUTF());
            int protocol = in.readInt();
            protocolVersions.put(uuid, protocol);
            Player target = Bukkit.getPlayer(uuid);
            if (target != null) {
                target.setMetadata("protocol_version", new org.bukkit.metadata.FixedMetadataValue(plugin, protocol));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse protocol version sync: " + e.getMessage());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        protocolVersions.remove(event.getPlayer().getUniqueId());
    }

    public int getProtocolVersion(UUID uuid) {
        return protocolVersions.getOrDefault(uuid, 765); // Default to 1.21 if unknown
    }
}
