package com.geysermc.tpaforms.hook;

import com.geysermc.tpaforms.util.Integrations;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.UUID;

/**
 * Hook for Floodgate to detect Bedrock players.
 *
 * <p>Floodgate is a SOFT dependency: if the plugin/API is absent every query returns false and the
 * plugin simply never treats anyone as a Bedrock player. All lookups here are UUID-based, which is
 * important on Folia - it means we never have to touch a {@link Player}'s region-owned state to
 * decide whether they are a Bedrock client, so this is safe to call from any thread.
 */
public class FloodgateHook {

    private FloodgateApi floodgateApi;
    private volatile boolean available = false;

    public FloodgateHook() {
        if (!Integrations.FLOODGATE_API) {
            Bukkit.getLogger().info("[TPAForms] Floodgate API not on the classpath.");
            return;
        }
        try {
            floodgateApi = FloodgateApi.getInstance();
            available = floodgateApi != null;
            if (available) {
                Bukkit.getLogger().info("[TPAForms] Floodgate hooked successfully.");
            } else {
                Bukkit.getLogger().warning("[TPAForms] Floodgate classes present but the API is not initialised.");
            }
        } catch (Throwable t) {
            available = false;
            Bukkit.getLogger().warning("[TPAForms] Failed to hook Floodgate: " + t);
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean isBedrockPlayer(Player player) {
        return player != null && isBedrockPlayer(player.getUniqueId());
    }

    public boolean isBedrockPlayer(UUID uuid) {
        if (!available || floodgateApi == null || uuid == null) {
            return false;
        }
        try {
            return floodgateApi.isFloodgatePlayer(uuid);
        } catch (Throwable t) {
            return false;
        }
    }

    public String getXuid(UUID uuid) {
        if (!available || floodgateApi == null) {
            return null;
        }
        try {
            var floodgatePlayer = floodgateApi.getPlayer(uuid);
            return floodgatePlayer != null ? floodgatePlayer.getXuid() : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
