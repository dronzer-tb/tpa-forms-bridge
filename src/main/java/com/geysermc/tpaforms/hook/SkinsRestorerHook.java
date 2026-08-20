package com.geysermc.tpaforms.hook;

import com.geysermc.tpaforms.util.Integrations;
import net.skinsrestorer.api.SkinsRestorer;
import net.skinsrestorer.api.SkinsRestorerProvider;
import org.bukkit.Bukkit;

/**
 * Hook for SkinsRestorer. Purely an avatar-URL provider - it touches no world state, so every
 * method here is thread-safe and callable from a form callback on the companion network thread.
 */
public class SkinsRestorerHook {

    private SkinsRestorer skinsRestorer;
    private volatile boolean enabled;

    public SkinsRestorerHook() {
        if (!Integrations.SKINSRESTORER_API || Bukkit.getPluginManager().getPlugin("SkinsRestorer") == null) {
            this.enabled = false;
            Bukkit.getLogger().info("[TPAForms] SkinsRestorer not found - using default avatar service.");
            return;
        }
        try {
            this.skinsRestorer = SkinsRestorerProvider.get();
            this.enabled = this.skinsRestorer != null;
            if (this.enabled) {
                Bukkit.getLogger().info("[TPAForms] SkinsRestorer hooked successfully.");
            }
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[TPAForms] Failed to hook SkinsRestorer: " + t);
            this.enabled = false;
        }
    }

    /** URL to a 2D avatar for the given player name. */
    public String getTextureUrl(String playerName) {
        return "https://mc-heads.net/avatar/" + playerName;
    }

    /** URL to a full-body render for the given player name. */
    public String getBodyTextureUrl(String playerName) {
        return "https://mc-heads.net/body/" + playerName;
    }

    /** URL to a 3D head render for the given player name. */
    public String getHeadTextureUrl(String playerName) {
        return "https://mc-heads.net/head/" + playerName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public SkinsRestorer getApi() {
        return skinsRestorer;
    }
}
