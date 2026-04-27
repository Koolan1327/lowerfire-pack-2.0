package dev.codex.lowerfire;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public final class LowerFirePlugin extends JavaPlugin implements Listener {

    private ResourcePackSettings resourcePackSettings;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadPackSettings();

        Bukkit.getPluginManager().registerEvents(this, this);

        for (Player player : Bukkit.getOnlinePlayers()) {
            applyPackLater(player, 20L);
        }
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        reloadPackSettings();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        applyPackLater(event.getPlayer(), 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        applyPackLater(event.getPlayer(), 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        applyPackLater(event.getPlayer(), 10L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        if (!resourcePackSettings.enabled()) {
            return;
        }

        PlayerResourcePackStatusEvent.Status status = event.getStatus();
        if (status != PlayerResourcePackStatusEvent.Status.ACCEPTED
                && status != PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            getLogger().warning(event.getPlayer().getName() + " did not apply the LowerFire resource pack: " + status);
        }
    }

    private void reloadPackSettings() {
        FileConfiguration config = getConfig();
        resourcePackSettings = ResourcePackSettings.fromConfig(config);

        if (!resourcePackSettings.enabled()) {
            getLogger().warning("Resource pack support is disabled. Set resource-pack.enabled to true after you configure the URL.");
            return;
        }

        if (resourcePackSettings.url().isBlank()) {
            getLogger().warning("resource-pack.url is blank. Players will not receive the LowerFire resource pack.");
            return;
        }

        getLogger().info("Configured LowerFire resource pack URL: " + resourcePackSettings.url());
    }

    private void applyPackLater(Player player, long delayTicks) {
        Bukkit.getScheduler().runTaskLater(this, () -> applyPack(player), delayTicks);
    }

    private void applyPack(Player player) {
        if (!player.isOnline() || !resourcePackSettings.enabled()) {
            return;
        }

        String url = resourcePackSettings.url();
        if (url.isBlank()) {
            return;
        }

        byte[] sha1 = resourcePackSettings.sha1();

        if (sha1.length == 20) {
            player.setResourcePack(url, sha1);
        } else {
            player.setResourcePack(url);
        }

        if (!resourcePackSettings.message().isBlank()) {
            player.sendMessage(resourcePackSettings.message());
        }
    }

    private record ResourcePackSettings(boolean enabled, String url, byte[] sha1, String message) {
        private static ResourcePackSettings fromConfig(FileConfiguration config) {
            boolean enabled = config.getBoolean("resource-pack.enabled", true);
            String url = config.getString("resource-pack.url", "").trim();
            String sha1Text = config.getString("resource-pack.sha1", "").trim();
            String message = config.getString("resource-pack.message", "").trim();

            return new ResourcePackSettings(enabled, url, parseSha1(sha1Text), message);
        }

        private static byte[] parseSha1(String value) {
            String normalized = value.toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                return new byte[0];
            }

            if (normalized.length() != 40) {
                return new byte[0];
            }

            byte[] result = new byte[20];
            for (int i = 0; i < result.length; i++) {
                int index = i * 2;
                try {
                    result[i] = (byte) Integer.parseInt(normalized.substring(index, index + 2), 16);
                } catch (NumberFormatException ignored) {
                    return new byte[0];
                }
            }
            return result;
        }
    }
}
