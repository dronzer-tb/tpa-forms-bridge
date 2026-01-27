package com.geysermc.tpaforms.menu;

import com.geysermc.tpaforms.TPAFormsPlugin;
import com.geysermc.tpaforms.config.PlayerSettings;
import com.geysermenu.companion.api.GeyserMenuAPI;
import org.bukkit.entity.Player;

/**
 * Settings form for configuring TPA preferences.
 * 
 * Allows players to toggle:
 * - Auto Accept: Automatically accept incoming TPA requests (not TPAHere)
 * - Forms Delivery: Show TPA requests as form popups instead of chat messages
 */
public class TpaSettingsForm {

    private final TPAFormsPlugin plugin;
    private final Player player;

    public TpaSettingsForm(TPAFormsPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        GeyserMenuAPI api = GeyserMenuAPI.getInstance();
        if (api == null) {
            plugin.getLogger().warning("GeyserMenuAPI not available");
            return;
        }

        PlayerSettings settings = plugin.getSettingsManager().getSettings(player.getUniqueId());

        api.createCustomMenu("§l§eTPA Settings", player.getUniqueId())
                .label("§7Configure your TPA preferences below:")
                .label("")
                .toggle("auto_accept", "§aAuto Accept TPA Requests", settings.isAutoAcceptTpa())
                .label("§8↳ Automatically accept incoming TPA requests")
                .label("§8   (Does not apply to TPAHere requests)")
                .label("")
                .toggle("forms_delivery", "§bForms Delivery", settings.isFormsDelivery())
                .label("§8↳ Show TPA requests as popup forms")
                .label("§8   (If disabled, requests appear in chat only)")
                .send(response -> {
                    if (response.wasClosed()) return;

                    Boolean autoAccept = response.getBoolean("auto_accept");
                    Boolean formsDelivery = response.getBoolean("forms_delivery");

                    if (autoAccept != null) {
                        settings.setAutoAcceptTpa(autoAccept);
                    }
                    if (formsDelivery != null) {
                        settings.setFormsDelivery(formsDelivery);
                    }

                    plugin.getSettingsManager().saveSettings(player.getUniqueId());

                    player.sendMessage("§aTPA Settings updated successfully!");
                    
                    // Log changes if debug is enabled
                    if (plugin.getConfig().getBoolean("debug", false)) {
                        plugin.getLogger().info("Settings updated for " + player.getName() + 
                                ": autoAccept=" + settings.isAutoAcceptTpa() + 
                                ", formsDelivery=" + settings.isFormsDelivery());
                    }
                });
    }
}
