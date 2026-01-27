package com.geysermc.tpaforms.config;

import com.geysermc.tpaforms.TPAFormsPlugin;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SettingsManager {
    private final TPAFormsPlugin plugin;
    private final Map<UUID, PlayerSettings> settingsMap = new ConcurrentHashMap<>();
    private final File userdataFolder;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public SettingsManager(TPAFormsPlugin plugin) {
        this.plugin = plugin;
        this.userdataFolder = new File(plugin.getDataFolder(), "userdata");
        if (!userdataFolder.exists()) {
            userdataFolder.mkdirs();
        }
    }

    public PlayerSettings getSettings(UUID uuid) {
        return settingsMap.computeIfAbsent(uuid, this::loadSettings);
    }

    private PlayerSettings loadSettings(UUID uuid) {
        File file = new File(userdataFolder, uuid.toString() + ".json");
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                return gson.fromJson(reader, PlayerSettings.class);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to load settings for " + uuid + ": " + e.getMessage());
            }
        }
        return new PlayerSettings();
    }

    public void saveSettings(UUID uuid) {
        PlayerSettings settings = settingsMap.get(uuid);
        if (settings == null) return;

        File file = new File(userdataFolder, uuid.toString() + ".json");
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(settings, writer);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save settings for " + uuid + ": " + e.getMessage());
        }
    }

    public void unloadSettings(UUID uuid) {
        saveSettings(uuid);
        settingsMap.remove(uuid);
    }
}
