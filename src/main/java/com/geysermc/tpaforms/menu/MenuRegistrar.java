package com.geysermc.tpaforms.menu;

import com.geysermc.tpaforms.TPAFormsPlugin;
import com.geysermenu.companion.api.BedrockPlayer;
import com.geysermenu.companion.api.GeyserMenuAPI;
import com.geysermenu.companion.api.MenuButton;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Registers the TPA button with the GeyserMenu companion plugin.
 * The button appears in the main GeyserMenu with an Ender Eye icon.
 */
public class MenuRegistrar {

    private final TPAFormsPlugin plugin;
    private static final String BUTTON_ID = "tpa-forms-main";

    public MenuRegistrar(TPAFormsPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerButton() {
        GeyserMenuAPI api = GeyserMenuAPI.getInstance();
        if (api == null) {
            plugin.getLogger().warning("GeyserMenuAPI is not available. TPA button will not be registered.");
            return;
        }

        plugin.getLogger().info("Registering TPA button with GeyserMenu...");

        MenuButton button = MenuButton.builder()
                .id(BUTTON_ID)
                .text("§bTPA")
                .imagePath("textures/items/ender_eye") // Ender eye logo as requested
                .priority(30) // Higher priority to appear near the top
                .condition(playerObj -> {
                    // The condition receives a BedrockPlayer object from the API
                    if (playerObj instanceof BedrockPlayer bedrockPlayer) {
                        Player bukkitPlayer = Bukkit.getPlayer(bedrockPlayer.getUuid());
                        if (bukkitPlayer != null) {
                            return bukkitPlayer.hasPermission("tpaforms.use");
                        }
                    }
                    // Default to showing the button
                    return true;
                })
                .onClick((playerObj, session) -> {
                    // The onClick receives a BedrockPlayer object from the API
                    if (playerObj instanceof BedrockPlayer bedrockPlayer) {
                        Player bukkitPlayer = Bukkit.getPlayer(bedrockPlayer.getUuid());
                        if (bukkitPlayer != null) {
                            // Open the main TPA menu
                            new TpaMainMenu(plugin, bukkitPlayer).open();
                        } else {
                            plugin.getLogger().warning("Could not find Bukkit player for: " + bedrockPlayer.getName());
                        }
                    }
                })
                .build();

        api.registerButton(button);
        plugin.getLogger().info("TPA button registered successfully with Ender Eye icon.");
    }

    public void unregisterButton() {
        GeyserMenuAPI api = GeyserMenuAPI.getInstance();
        if (api != null) {
            api.unregisterButton(BUTTON_ID);
            plugin.getLogger().info("TPA button unregistered.");
        }
    }
}
