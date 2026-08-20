package com.geysermc.tpaforms.menu;

import com.geysermc.tpaforms.TPAFormsPlugin;
import com.geysermc.tpaforms.util.Integrations;
import com.geysermc.tpaforms.util.Schedulers;
import com.geysermenu.companion.api.BedrockPlayer;
import com.geysermenu.companion.api.GeyserMenuAPI;
import com.geysermenu.companion.api.MenuButton;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registers the TPA button with the GeyserMenu companion plugin.
 *
 * <p>Folia notes:
 * <ul>
 *   <li>Both the {@code condition} and {@code onClick} callbacks are invoked by the companion's
 *       network thread, not by a region thread.</li>
 *   <li>{@code onClick} does no region work itself - it hands off to {@link TpaMainMenu}, which
 *       only builds/sends a form, and everything that actually touches the player goes through
 *       {@link com.geysermc.tpaforms.util.Commands}.</li>
 *   <li>{@code condition} has to return a value synchronously, so it cannot hop to a region
 *       thread (blocking a network thread on a region thread risks deadlock). It is answered from
 *       a permission cache refreshed on the global region thread instead, so no
 *       {@code Player} state is read off-thread.</li>
 * </ul>
 */
public class MenuRegistrar {

    private static final String BUTTON_ID = "tpa-forms-main";

    private final TPAFormsPlugin plugin;
    /** uuid -> may use the TPA button. Written on the global region thread, read off-thread. */
    private final Map<UUID, Boolean> permissionCache = new ConcurrentHashMap<>();

    public MenuRegistrar(TPAFormsPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerButton() {
        if (!Integrations.GEYSER_MENU_API) {
            return;
        }
        GeyserMenuAPI api = GeyserMenuAPI.getInstance();
        if (api == null) {
            plugin.getLogger().warning("GeyserMenuAPI is not available. TPA button will not be registered.");
            return;
        }

        plugin.getLogger().info("Registering TPA button with GeyserMenu...");

        MenuButton button = MenuButton.builder()
                .id(BUTTON_ID)
                .text("§bTPA")
                .imagePath("textures/items/ender_eye")
                .priority(30)
                .condition(playerObj -> {
                    if (playerObj instanceof BedrockPlayer bedrockPlayer) {
                        UUID uuid = bedrockPlayer.getUuid();
                        Boolean cached = permissionCache.get(uuid);
                        // Refresh for next time on the global region thread; answer optimistically now.
                        refreshPermission(uuid);
                        return cached == null || cached;
                    }
                    return true;
                })
                .onClick((playerObj, session) -> {
                    if (playerObj instanceof BedrockPlayer bedrockPlayer) {
                        // Do NOT resolve/inspect the Bukkit Player on this thread; TpaMainMenu only
                        // needs the UUID and every action it triggers is region-scheduled.
                        new TpaMainMenu(plugin, bedrockPlayer.getUuid()).open();
                    }
                })
                .build();

        api.registerButton(button);
        plugin.getLogger().info("TPA button registered successfully with Ender Eye icon.");
    }

    private void refreshPermission(UUID uuid) {
        Schedulers.global(plugin, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                permissionCache.remove(uuid);
            } else {
                permissionCache.put(uuid, player.hasPermission("tpaforms.use"));
            }
        });
    }

    public void unregisterButton() {
        if (!Integrations.GEYSER_MENU_API) {
            return;
        }
        GeyserMenuAPI api = GeyserMenuAPI.getInstance();
        if (api != null) {
            api.unregisterButton(BUTTON_ID);
            plugin.getLogger().info("TPA button unregistered.");
        }
        permissionCache.clear();
    }
}
