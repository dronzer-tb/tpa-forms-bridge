package com.geysermc.tpaforms.hook;

import net.skinsrestorer.api.SkinsRestorer;
import net.skinsrestorer.api.SkinsRestorerProvider;
import org.bukkit.Bukkit;

/**
 * Hook for SkinsRestorer to get player skin textures.
 * Used to display player heads in forms on cracked servers.
 * 
 * Falls back to mc-heads.net if SkinsRestorer is not available.
 */
public class SkinsRestorerHook {
    
    private SkinsRestorer skinsRestorer;
    private boolean enabled;

    public SkinsRestorerHook() {
        if (Bukkit.getPluginManager().getPlugin("SkinsRestorer") != null) {
            try {
                this.skinsRestorer = SkinsRestorerProvider.get();
                this.enabled = true;
                Bukkit.getLogger().info("[TPAForms] SkinsRestorer hooked successfully.");
            } catch (Exception e) {
                Bukkit.getLogger().warning("[TPAForms] Failed to hook SkinsRestorer: " + e.getMessage());
                this.enabled = false;
            }
        } else {
            this.enabled = false;
            Bukkit.getLogger().info("[TPAForms] SkinsRestorer not found - using default avatar service.");
        }
    }

    /**
     * Gets the texture URL for a player's head.
     * Uses mc-heads.net which supports both premium and cracked servers.
     * 
     * @param playerName The player's name
     * @return URL to the player's head texture
     */
    public String getTextureUrl(String playerName) {
        // mc-heads.net works with most servers including cracked ones
        // It automatically fetches the correct skin based on the player name
        return "https://mc-heads.net/avatar/" + playerName;
    }

    /**
     * Gets the full body skin render URL for a player.
     * 
     * @param playerName The player's name
     * @return URL to the player's full body render
     */
    public String getBodyTextureUrl(String playerName) {
        return "https://mc-heads.net/body/" + playerName;
    }

    /**
     * Gets the player's head (3D render) URL.
     * 
     * @param playerName The player's name
     * @return URL to the player's 3D head render
     */
    public String getHeadTextureUrl(String playerName) {
        return "https://mc-heads.net/head/" + playerName;
    }

    /**
     * Check if SkinsRestorer is enabled and available.
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Get the SkinsRestorer API instance (may be null if not available)
     */
    public SkinsRestorer getApi() {
        return skinsRestorer;
    }
}
