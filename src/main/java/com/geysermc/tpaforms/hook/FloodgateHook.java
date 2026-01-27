package com.geysermc.tpaforms.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.UUID;

/**
 * Hook for Floodgate to detect Bedrock players.
 */
public class FloodgateHook {

    private FloodgateApi floodgateApi;
    private boolean available = false;

    public FloodgateHook() {
        checkAvailability();
    }

    private void checkAvailability() {
        try {
            if (Bukkit.getPluginManager().getPlugin("floodgate") != null) {
                Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                floodgateApi = FloodgateApi.getInstance();
                available = floodgateApi != null;
                if (available) {
                    Bukkit.getLogger().info("[TPAForms] Floodgate hooked successfully.");
                }
            }
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            available = false;
            Bukkit.getLogger().warning("[TPAForms] Floodgate not found or not loaded.");
        }
    }

    /**
     * Check if Floodgate is available.
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * Check if a player is from Bedrock Edition.
     */
    public boolean isBedrockPlayer(Player player) {
        if (!available || floodgateApi == null) {
            return false;
        }
        return floodgateApi.isFloodgatePlayer(player.getUniqueId());
    }

    /**
     * Check if a player UUID belongs to a Bedrock player.
     */
    public boolean isBedrockPlayer(UUID uuid) {
        if (!available || floodgateApi == null) {
            return false;
        }
        return floodgateApi.isFloodgatePlayer(uuid);
    }

    /**
     * Get the XUID of a Bedrock player.
     */
    public String getXuid(UUID uuid) {
        if (!available || floodgateApi == null) {
            return null;
        }
        var floodgatePlayer = floodgateApi.getPlayer(uuid);
        return floodgatePlayer != null ? floodgatePlayer.getXuid() : null;
    }
}
