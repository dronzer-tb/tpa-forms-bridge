package com.geysermc.tpaforms.menu;

import com.geysermc.tpaforms.TPAFormsPlugin;
import com.geysermc.tpaforms.config.PlayerSettings;
import com.geysermc.tpaforms.util.Chat;
import com.geysermc.tpaforms.util.Integrations;
import com.geysermenu.companion.api.GeyserMenuAPI;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Settings form for configuring TPA preferences.
 *
 * <p>Folia notes: the response callback arrives on the companion network thread and mutates the
 * shared {@link PlayerSettings} instance, whose fields are volatile for that reason. The resulting
 * disk write is dispatched to the async scheduler, never done inline on a region thread.
 */
public class TpaSettingsForm {

    private final TPAFormsPlugin plugin;
    private final UUID playerId;

    public TpaSettingsForm(TPAFormsPlugin plugin, Player player) {
        this(plugin, player.getUniqueId());
    }

    public TpaSettingsForm(TPAFormsPlugin plugin, UUID playerId) {
        this.plugin = plugin;
        this.playerId = playerId;
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

        PlayerSettings settings = plugin.getSettingsManager().getSettings(playerId);

        api.createCustomMenu("§l§eTPA Settings", playerId)
                .label("§7Configure your TPA preferences below:")
                .label("")
                .toggle("auto_accept", "§aAuto Accept TPA Requests", settings.isAutoAcceptTpa())
                .label("§8↳ Automatically accept incoming TPA requests")
                .label("§8   (Does not apply to TPAHere requests)")
                .label("")
                .toggle("forms_delivery", "§bForms Delivery", settings.isFormsDelivery())
                .label("§8↳ Show TPA requests as popup forms")
                .label("§8   (If disabled, requests appear in chat only)")
                .label("")
                .toggle("accepting", "§dAccept Teleport Requests", settings.isAcceptingRequests())
                .label("§8↳ Same switch as /tpatoggle - when off, nobody")
                .label("§8   can send you a teleport request at all")
                .send(response -> {
                    if (response.wasClosed()) {
                        return;
                    }

                    Boolean autoAccept = response.getBoolean("auto_accept");
                    Boolean formsDelivery = response.getBoolean("forms_delivery");
                    Boolean accepting = response.getBoolean("accepting");

                    if (autoAccept != null) {
                        settings.setAutoAcceptTpa(autoAccept);
                    }
                    if (formsDelivery != null) {
                        settings.setFormsDelivery(formsDelivery);
                    }
                    if (accepting != null) {
                        settings.setAcceptingRequests(accepting);
                    }

                    plugin.getSettingsManager().saveSettingsAsync(playerId);
                    Chat.send(plugin, playerId, plugin.getMessages().get("settings-updated"));

                    if (plugin.getConfig().getBoolean("debug", false)) {
                        plugin.getLogger().info("Settings updated for " + playerId + ": " + settings);
                    }
                });
    }
}
