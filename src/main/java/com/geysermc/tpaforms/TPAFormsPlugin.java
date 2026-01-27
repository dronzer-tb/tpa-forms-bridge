package com.geysermc.tpaforms;

import com.geysermc.tpaforms.config.SettingsManager;
import com.geysermc.tpaforms.hook.EssentialsHook;
import com.geysermc.tpaforms.hook.FloodgateHook;
import com.geysermc.tpaforms.hook.SkinsRestorerHook;
import com.geysermc.tpaforms.menu.MenuRegistrar;
import lombok.Getter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * TPA Forms Bridge v2 - Spigot-side TPA Forms for Geyser/Bedrock players.
 * 
 * Features:
 * - TPA/TPAHere request forms for Bedrock players
 * - Player list with skin support via SkinsRestorer
 * - Auto-accept toggle for regular TPA requests
 * - Forms delivery toggle (enable/disable form popups)
 * - GeyserMenu integration via companion plugin
 */
@Getter
public class TPAFormsPlugin extends JavaPlugin implements Listener {

    @Getter
    private static TPAFormsPlugin instance;
    
    private SettingsManager settingsManager;
    private FloodgateHook floodgateHook;
    private SkinsRestorerHook skinsRestorerHook;
    private MenuRegistrar menuRegistrar;

    @Override
    public void onEnable() {
        instance = this;
        
        // Save default config
        saveDefaultConfig();

        getLogger().info("=== TPA Forms Bridge v2 Starting ===");

        // Initialize Floodgate hook (for Bedrock player detection)
        this.floodgateHook = new FloodgateHook();
        if (!floodgateHook.isAvailable()) {
            getLogger().severe("Floodgate not found! This plugin requires Floodgate.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize Settings Manager
        this.settingsManager = new SettingsManager(this);
        getLogger().info("Settings manager initialized.");

        // Initialize SkinsRestorer Hook (optional)
        this.skinsRestorerHook = new SkinsRestorerHook();
        if (skinsRestorerHook.isEnabled()) {
            getLogger().info("SkinsRestorer integration enabled - player skins will show in forms.");
        } else {
            getLogger().info("SkinsRestorer not found - using default avatar service.");
        }

        // Register Menu Button with GeyserMenu (delayed to ensure companion is connected)
        this.menuRegistrar = new MenuRegistrar(this);
        // Delay registration by 2 seconds to ensure GeyserMenuCompanion is fully connected
        getServer().getScheduler().runTaskLater(this, () -> {
            this.menuRegistrar.registerButton();
        }, 40L); // 40 ticks = 2 seconds

        // Register Essentials Event Listener for TPA interception
        if (getServer().getPluginManager().getPlugin("Essentials") != null) {
            getServer().getPluginManager().registerEvents(new EssentialsHook(this), this);
            getLogger().info("Hooked into EssentialsX - TPA forms will be shown to Bedrock players.");
        } else {
            getLogger().severe("EssentialsX not found! TPA features will not work.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Register this class as listener for player quit (to save settings)
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("TPA Forms Bridge v2 enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (menuRegistrar != null) {
            menuRegistrar.unregisterButton();
        }
        instance = null;
        getLogger().info("TPA Forms Bridge v2 disabled.");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Save and unload player settings when they leave
        if (settingsManager != null) {
            settingsManager.unloadSettings(event.getPlayer().getUniqueId());
        }
    }
}
