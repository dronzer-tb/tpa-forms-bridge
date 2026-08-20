package com.geysermc.tpaforms;

import com.geysermc.tpaforms.command.TpaCommand;
import com.geysermc.tpaforms.config.Messages;
import com.geysermc.tpaforms.config.SettingsManager;
import com.geysermc.tpaforms.hook.FloodgateHook;
import com.geysermc.tpaforms.hook.SkinsRestorerHook;
import com.geysermc.tpaforms.menu.MenuRegistrar;
import com.geysermc.tpaforms.tpa.TpaListener;
import com.geysermc.tpaforms.tpa.TpaManager;
import com.geysermc.tpaforms.util.Integrations;
import com.geysermc.tpaforms.util.Schedulers;
import com.geysermenu.companion.api.GeyserMenuAPI;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

/**
 * TPA Forms Bridge v2 - a self-contained TPA system serving both Java and Bedrock players.
 *
 * <p><b>What changed in this version.</b> The plugin used to be a bridge: it dispatched
 * {@code /tpa} etc. at Essentials and listened for {@code net.ess3.api.events.TPARequestEvent} to
 * catch incoming requests. That event is an EssentialsX class; the plugin installed on this server
 * is "EssentialsC" ({@code net.godlycow.org.essc}), which merely resembles EssentialsX and exposes
 * only Home/Kit/Rtp/Warp events. Incoming requests could therefore never be intercepted, which
 * meant a Bedrock player could never be shown an incoming-request form - the single most valuable
 * thing this plugin was supposed to do. The whole TPA flow now lives in {@link TpaManager} and the
 * plugin has no dependency on Essentials of any kind.
 *
 * <p><b>Folia port notes.</b>
 * <ul>
 *   <li>{@code folia-supported: true}; no {@code Bukkit.getScheduler()} / {@code BukkitRunnable}
 *       anywhere in the plugin (see {@link Schedulers}).</li>
 *   <li>Every third-party plugin is a soft dependency, probed via {@link Integrations} before any
 *       of its classes are touched.</li>
 *   <li>{@code instance} is volatile: onEnable/onDisable run on the global region thread while
 *       readers can be on any region thread.</li>
 * </ul>
 */
public class TPAFormsPlugin extends JavaPlugin implements Listener {

    private static final List<String> COMMANDS = List.of(
            "tpa", "tpahere", "tpaccept", "tpdeny", "tpcancel", "tpatoggle", "tpaignore", "tpaqueue");

    private static volatile TPAFormsPlugin instance;

    private SettingsManager settingsManager;
    private Messages messages;
    private TpaManager tpaManager;
    private FloodgateHook floodgateHook;
    private SkinsRestorerHook skinsRestorerHook;
    private MenuRegistrar menuRegistrar;

    public static TPAFormsPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        getLogger().info("=== TPA Forms Bridge v2 (self-contained, Folia-compatible) starting ===");

        // ---- Settings & messages -------------------------------------------------------
        this.settingsManager = new SettingsManager(this);
        this.messages = new Messages();
        this.messages.reload(this);

        // ---- Floodgate (soft) ----------------------------------------------------------
        this.floodgateHook = new FloodgateHook();
        if (!floodgateHook.isAvailable()) {
            // Soft, not fatal: without Floodgate nobody is classified as Bedrock, so every player
            // gets the chat flow. The TPA engine itself is unaffected.
            getLogger().warning("Floodgate not present - no player will be treated as Bedrock, "
                    + "so no TPA forms will be delivered. Chat-based TPA still works for everyone.");
        }

        // ---- SkinsRestorer (soft) ------------------------------------------------------
        this.skinsRestorerHook = new SkinsRestorerHook();
        getLogger().info(skinsRestorerHook.isEnabled()
                ? "SkinsRestorer integration enabled - player skins will show in forms."
                : "SkinsRestorer not found - using the default avatar service.");

        // ---- TPA engine ----------------------------------------------------------------
        this.tpaManager = new TpaManager(this, messages);
        this.tpaManager.start();
        registerCommands();

        // ---- GeyserMenuCompanion (soft) ------------------------------------------------
        if (Integrations.GEYSER_MENU_API) {
            this.menuRegistrar = new MenuRegistrar(this);
            // Folia: was Bukkit.getScheduler().runTaskLater(...). The button registration touches
            // only plugin-global state, so it belongs on the global region scheduler.
            Schedulers.globalDelayed(this, () -> menuRegistrar.registerButton(), 40L);
        } else {
            getLogger().warning("GeyserMenuCompanion not present - the TPA button and all Bedrock "
                    + "forms are disabled. Install GeyserMenuCompanion to enable them.");
        }

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new TpaListener(this), this);

        if (Integrations.ESSENTIALS_TPA_EVENT) {
            // Purely informational. We do not hook the event any more - we own the flow - but two
            // plugins both owning /tpa is a configuration problem worth shouting about.
            getLogger().warning("EssentialsX's TPARequestEvent is on the classpath. This plugin now "
                    + "implements TPA itself; disable the other plugin's tpa/tpahere/tpaccept/tpdeny/"
                    + "tpcancel/tpaignore/tpatoggle/tpaqueue commands to avoid them competing.");
        }

        getLogger().info("TPA Forms Bridge v2 enabled (floodgate=" + Integrations.FLOODGATE_API
                + ", geysermenu=" + Integrations.GEYSER_MENU_API
                + ", skinsrestorer=" + Integrations.SKINSRESTORER_API + ")");
    }

    /**
     * Binds all eight commands to the shared executor. They are declared in plugin.yml, so a null
     * here means the yml and this list have drifted apart - worth a loud warning rather than a
     * silent dead command.
     */
    private void registerCommands() {
        TpaCommand executor = new TpaCommand(this);
        for (String name : COMMANDS) {
            PluginCommand command = getCommand(name);
            if (command == null) {
                getLogger().severe("Command /" + name + " is missing from plugin.yml - it will not work.");
                continue;
            }
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
    }

    @Override
    public void onDisable() {
        if (tpaManager != null) {
            tpaManager.stop();
        }
        Schedulers.cancelAll(this);
        if (menuRegistrar != null) {
            menuRegistrar.unregisterButton();
        }
        if (settingsManager != null) {
            // The async scheduler is not guaranteed to drain during shutdown, so flush inline.
            settingsManager.flushAllBlocking();
        }
        instance = null;
        getLogger().info("TPA Forms Bridge v2 disabled.");
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        if (messages != null) {
            messages.reload(this);
        }
        if (tpaManager != null) {
            tpaManager.reloadSettings();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Warm the settings cache off-thread so the request path never blocks a region thread on a
        // disk read.
        if (settingsManager != null) {
            settingsManager.preload(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (tpaManager != null) {
            // Drops every request involving them and kills any in-flight warmup. Only touches
            // plugin-owned concurrent state, so it is safe on this (the player's) region thread.
            tpaManager.handleQuit(uuid);
        }
        if (settingsManager != null) {
            // Folia: this fires on the player's region thread. The write itself is dispatched to
            // the async scheduler inside unloadSettings().
            settingsManager.unloadSettings(uuid);
        }
    }

    /**
     * Whether a player should get Bedrock forms. Floodgate is the primary source; the companion
     * plugin's own session table is the fallback so forms still work if Floodgate is missing but
     * Geyser is proxying the player. Both are UUID-only lookups and therefore thread-safe.
     */
    public boolean isBedrockPlayer(UUID uuid) {
        if (floodgateHook != null && floodgateHook.isBedrockPlayer(uuid)) {
            return true;
        }
        if (!Integrations.GEYSER_MENU_API) {
            return false;
        }
        try {
            GeyserMenuAPI api = GeyserMenuAPI.getInstance();
            return api != null && api.isBedrockPlayer(uuid);
        } catch (Throwable t) {
            return false;
        }
    }

    public SettingsManager getSettingsManager() {
        return settingsManager;
    }

    public Messages getMessages() {
        return messages;
    }

    public TpaManager getTpaManager() {
        return tpaManager;
    }

    public FloodgateHook getFloodgateHook() {
        return floodgateHook;
    }

    public SkinsRestorerHook getSkinsRestorerHook() {
        return skinsRestorerHook;
    }

    public MenuRegistrar getMenuRegistrar() {
        return menuRegistrar;
    }
}
