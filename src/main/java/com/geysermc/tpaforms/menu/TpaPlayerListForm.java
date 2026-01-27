package com.geysermc.tpaforms.menu;

import com.geysermc.tpaforms.TPAFormsPlugin;
import com.geysermenu.companion.api.GeyserMenuAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Form that displays a list of online players to send TPA/TPAHere requests.
 * Shows player skins using SkinsRestorer integration.
 */
public class TpaPlayerListForm {

    private final TPAFormsPlugin plugin;
    private final Player viewer;

    public TpaPlayerListForm(TPAFormsPlugin plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
    }

    public void open() {
        GeyserMenuAPI api = GeyserMenuAPI.getInstance();
        if (api == null) {
            plugin.getLogger().warning("GeyserMenuAPI not available");
            return;
        }

        // Get all online players except the viewer
        List<Player> onlinePlayers = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.equals(viewer)) {
                onlinePlayers.add(player);
            }
        }

        if (onlinePlayers.isEmpty()) {
            api.createSimpleMenu("§l§bTeleport", viewer.getUniqueId())
                    .content("§7There are no other players online.")
                    .button("§cClose")
                    .send(response -> {});
            return;
        }

        var menuBuilder = api.createSimpleMenu("§l§bTeleport", viewer.getUniqueId())
                .content("§7Select a player to send a teleport request:");

        // Add buttons for each online player with their skin
        for (Player player : onlinePlayers) {
            String skinUrl = plugin.getSkinsRestorerHook().getTextureUrl(player.getName());
            menuBuilder.button("§f" + player.getName(), skinUrl);
        }

        menuBuilder.send(response -> {
            if (response.wasClosed()) return;

            int buttonId = response.getButtonId();
            if (buttonId >= 0 && buttonId < onlinePlayers.size()) {
                Player selectedPlayer = onlinePlayers.get(buttonId);
                // Open the TPA action menu for the selected player
                openActionMenu(selectedPlayer);
            }
        });
    }

    /**
     * Opens a submenu to choose between TPA, TPAHere, or Settings
     */
    private void openActionMenu(Player selectedPlayer) {
        GeyserMenuAPI api = GeyserMenuAPI.getInstance();
        if (api == null) return;

        String skinUrl = plugin.getSkinsRestorerHook().getTextureUrl(selectedPlayer.getName());

        api.createSimpleMenu("§l§b" + selectedPlayer.getName(), viewer.getUniqueId())
                .content("§7What would you like to do?")
                .button("§a➡ Teleport to them (TPA)", skinUrl)
                .button("§e⬅ Teleport them to you (TPAHere)", skinUrl)
                .button("§8↩ Back to player list")
                .send(response -> {
                    if (response.wasClosed()) return;

                    int buttonId = response.getButtonId();
                    switch (buttonId) {
                        case 0 -> {
                            // TPA - Request to teleport TO the selected player
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                viewer.performCommand("tpa " + selectedPlayer.getName());
                            });
                            viewer.sendMessage("§aSent teleport request to §b" + selectedPlayer.getName() + "§a.");
                        }
                        case 1 -> {
                            // TPAHere - Request the selected player to teleport to YOU
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                viewer.performCommand("tpahere " + selectedPlayer.getName());
                            });
                            viewer.sendMessage("§aSent teleport here request to §b" + selectedPlayer.getName() + "§a.");
                        }
                        case 2 -> {
                            // Back to player list
                            open();
                        }
                    }
                });
    }
}
