package com.geysermc.tpaforms.config;

import com.geysermc.tpaforms.TPAFormsPlugin;
import com.geysermc.tpaforms.util.Schedulers;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores per-player TPA toggles, backed by one JSON file per player.
 *
 * <p>Folia notes:
 * <ul>
 *   <li>{@code settingsMap} is a {@link ConcurrentHashMap} - it is touched from region threads
 *       (event handlers), from the GeyserMenuCompanion network thread (form callbacks) and from
 *       the async scheduler (disk writes), all of which run concurrently.</li>
 *   <li>Disk I/O never happens on a region thread. {@link #saveSettingsAsync(UUID)} hops to the
 *       async scheduler; only {@link #flushAllBlocking()} (called from onDisable, when schedulers
 *       are already shutting down) writes on the calling thread.</li>
 *   <li>A per-UUID lock object keeps two concurrent writers off the same file.</li>
 * </ul>
 */
public class SettingsManager {

    private final TPAFormsPlugin plugin;
    private final Map<UUID, PlayerSettings> settingsMap = new ConcurrentHashMap<>();
    private final Map<UUID, Object> fileLocks = new ConcurrentHashMap<>();
    private final File userdataFolder;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public SettingsManager(TPAFormsPlugin plugin) {
        this.plugin = plugin;
        this.userdataFolder = new File(plugin.getDataFolder(), "userdata");
        if (!userdataFolder.exists() && !userdataFolder.mkdirs() && !userdataFolder.isDirectory()) {
            plugin.getLogger().warning("Could not create userdata folder: " + userdataFolder);
        }
    }

    /**
     * Returns (loading from disk on first touch) the settings for a player.
     *
     * <p>This can block on disk on the very first call for a player. It is invoked from the TPA
     * event handler, so keep the files tiny; alternatively call {@link #preload(UUID)} on join.
     */
    public PlayerSettings getSettings(UUID uuid) {
        return settingsMap.computeIfAbsent(uuid, this::loadSettings);
    }

    /** Warms the cache off-thread so {@link #getSettings(UUID)} never blocks on a region thread. */
    public void preload(UUID uuid) {
        Schedulers.async(plugin, () -> getSettings(uuid));
    }

    private PlayerSettings loadSettings(UUID uuid) {
        File file = new File(userdataFolder, uuid + ".json");
        synchronized (lockFor(uuid)) {
            if (file.isFile()) {
                try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                    PlayerSettings loaded = gson.fromJson(reader, PlayerSettings.class);
                    // gson returns null for an empty/blank file - the old code stuffed that null
                    // straight back out of computeIfAbsent and NPE'd at the call site.
                    if (loaded != null) {
                        // Gson writes the fields reflectively and can leave `ignored` as a plain
                        // HashSet (or null); normalize() restores the concurrent collection before
                        // the instance becomes visible to other threads.
                        return loaded.normalize();
                    }
                } catch (IOException | JsonSyntaxException e) {
                    plugin.getLogger().warning("Failed to load settings for " + uuid + ": " + e.getMessage());
                }
            }
        }
        return new PlayerSettings();
    }

    /** Queues a save on the async scheduler. Safe to call from any thread, region or otherwise. */
    public void saveSettingsAsync(UUID uuid) {
        Schedulers.async(plugin, () -> saveSettingsBlocking(uuid));
    }

    private void saveSettingsBlocking(UUID uuid) {
        PlayerSettings settings = settingsMap.get(uuid);
        if (settings == null) {
            return;
        }
        File file = new File(userdataFolder, uuid + ".json");
        synchronized (lockFor(uuid)) {
            try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                gson.toJson(settings, writer);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to save settings for " + uuid + ": " + e.getMessage());
            }
        }
    }

    /** Saves then drops a player's settings. Called on quit; the write happens off-thread. */
    public void unloadSettings(UUID uuid) {
        Schedulers.async(plugin, () -> {
            saveSettingsBlocking(uuid);
            settingsMap.remove(uuid);
            fileLocks.remove(uuid);
        });
    }

    /** Synchronous flush for onDisable, where the async scheduler is no longer guaranteed to run. */
    public void flushAllBlocking() {
        for (UUID uuid : settingsMap.keySet()) {
            saveSettingsBlocking(uuid);
        }
    }

    private Object lockFor(UUID uuid) {
        return fileLocks.computeIfAbsent(uuid, k -> new Object());
    }
}
