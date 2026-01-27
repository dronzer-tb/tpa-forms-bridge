package com.geysermc.tpaforms.menu;

import com.geysermc.tpaforms.TPAFormsPlugin;
import com.geysermenu.companion.api.GeyserMenuAPI;
import org.bukkit.entity.Player;

/**
 * Form that displays a TPA/TPAHere request to a Bedrock player.
 * Shows an accept/decline modal form with the requester's skin.
 */
public class TpaRequestForm {

    private final TPAFormsPlugin plugin;
    private final Player target;
    private final Player requester;
    private final boolean isTpaHere;

    public TpaRequestForm(TPAFormsPlugin plugin, Player target, Player requester, boolean isTpaHere) {
        this.plugin = plugin;
        this.target = target;
        this.requester = requester;
        this.isTpaHere = isTpaHere;
    }

    public void open() {
        GeyserMenuAPI api = GeyserMenuAPI.getInstance();
        if (api == null) {
            plugin.getLogger().warning("GeyserMenuAPI not available - falling back to chat");
            return;
        }

        // Build the content message
        String content;
        if (isTpaHere) {
            content = "§b" + requester.getName() + "§r wants you to teleport to them.\n\n§7Do you accept this request?";
        } else {
            content = "§b" + requester.getName() + "§r wants to teleport to you.\n\n§7Do you accept this request?";
        }

        // Get the requester's skin URL for the form
        String skinUrl = plugin.getSkinsRestorerHook().getTextureUrl(requester.getName());

        api.createModalMenu("§lTeleport Request", target.getUniqueId())
                .content(content)
                .button("§a✔ Accept")
                .button("§c✖ Deny")
                .send(response -> {
                    if (response.wasClosed()) {
                        // Player closed the form without responding - treat as deny
                        target.sendMessage("§7Teleport request from §b" + requester.getName() + "§7 was ignored.");
                        return;
                    }

                    int buttonId = response.getButtonId();
                    if (buttonId == 0) {
                        // Accept button clicked
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            target.performCommand("tpaccept");
                        });
                    } else {
                        // Deny button clicked
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            target.performCommand("tpdeny");
                        });
                    }
                });
    }
}
