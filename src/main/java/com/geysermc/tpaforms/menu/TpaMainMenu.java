package com.geysermc.tpaforms.menu;

import com.geysermc.tpaforms.TPAFormsPlugin;
import com.geysermenu.companion.api.GeyserMenuAPI;
import org.bukkit.entity.Player;

/**
 * Main TPA menu that shows options for TPA actions and settings.
 */
public class TpaMainMenu {

    private final TPAFormsPlugin plugin;
    private final Player player;

    public TpaMainMenu(TPAFormsPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        GeyserMenuAPI api = GeyserMenuAPI.getInstance();
        if (api == null) {
            plugin.getLogger().warning("GeyserMenuAPI not available");
            return;
        }

        api.createSimpleMenu("§l§bTPA", player.getUniqueId())
                .content("§7Select an option:")
                .button("§a➡ Send TPA Request", "https://mc-heads.net/avatar/MHF_Steve")
                .button("§e⚙ TPA Settings", "textures/ui/settings_glyph_color_2x")
                .send(response -> {
                    if (response.wasClosed()) return;

                    int buttonId = response.getButtonId();
                    switch (buttonId) {
                        case 0 -> {
                            // Open player list to send TPA
                            new TpaPlayerListForm(plugin, player).open();
                        }
                        case 1 -> {
                            // Open TPA settings
                            new TpaSettingsForm(plugin, player).open();
                        }
                    }
                });
    }
}
