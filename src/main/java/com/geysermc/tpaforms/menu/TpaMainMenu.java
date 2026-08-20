package com.geysermc.tpaforms.menu;

import com.geysermc.tpaforms.TPAFormsPlugin;
import com.geysermc.tpaforms.util.Integrations;
import com.geysermenu.companion.api.GeyserMenuAPI;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Main TPA menu. Holds the viewer as a UUID only - see {@link TpaRequestForm} for why.
 */
public class TpaMainMenu {

    private final TPAFormsPlugin plugin;
    private final UUID viewerId;

    public TpaMainMenu(TPAFormsPlugin plugin, Player player) {
        this(plugin, player.getUniqueId());
    }

    public TpaMainMenu(TPAFormsPlugin plugin, UUID viewerId) {
        this.plugin = plugin;
        this.viewerId = viewerId;
    }

    public void open() {
        if (!Integrations.GEYSER_MENU_API) {
            return;
        }
        GeyserMenuAPI api = GeyserMenuAPI.getInstance();
        if (api == null) {
            plugin.getLogger().warning("GeyserMenuAPI not available");
            return;
        }

        api.createSimpleMenu("§l§bTPA", viewerId)
                .content("§7Select an option:")
                .button("§a➡ Send TPA Request", "https://mc-heads.net/avatar/MHF_Steve")
                .button("§d\u2709 Pending Requests", "textures/ui/friend_glyph_color_2x")
                .button("§e⚙ TPA Settings", "textures/ui/settings_glyph_color_2x")
                .send(response -> {
                    if (response.wasClosed()) {
                        return;
                    }
                    switch (response.getButtonId()) {
                        case 0 -> new TpaPlayerListForm(plugin, viewerId).open();
                        case 1 -> new TpaPendingForm(plugin, viewerId).open();
                        case 2 -> new TpaSettingsForm(plugin, viewerId).open();
                        default -> { }
                    }
                });
    }
}
