package com.geysermc.tpaforms.menu;

import com.geysermc.tpaforms.TPAFormsPlugin;
import com.geysermc.tpaforms.tpa.TpaRequest;
import com.geysermc.tpaforms.util.Chat;
import com.geysermc.tpaforms.util.Integrations;
import com.geysermenu.companion.api.GeyserMenuAPI;

import java.util.UUID;

/**
 * The incoming-request popup shown to a Bedrock target.
 *
 * <p>This is the form that could never fire before. The old build waited for EssentialsX's
 * {@code TPARequestEvent} to learn about an incoming request, and the plugin actually installed on
 * this server ("EssentialsC") does not publish that event at all, so a Bedrock player was only ever
 * shown chat. The request now originates inside this plugin, so
 * {@link com.geysermc.tpaforms.tpa.TpaManager} can call this directly the moment a request is
 * created.
 *
 * <h2>Folia</h2>
 * The form is built on the global region thread and the response callback arrives much later on
 * the GeyserMenuCompanion network thread. Nothing but the request's identity (a UUID pair plus
 * cached names) crosses that gap - holding a {@code Player} across it and then calling into it
 * would be a cross-region access. The callback hands the decision straight back to
 * {@code TpaManager.resolveById}, which hops to the global region thread and re-validates that the
 * request still exists, because by the time a Bedrock player taps a button the request may already
 * have expired or been answered in chat.
 */
public class TpaRequestForm {

    private final TPAFormsPlugin plugin;
    private final UUID targetId;
    private final UUID requestId;
    private final String requesterName;
    private final boolean isTpaHere;

    public TpaRequestForm(TPAFormsPlugin plugin, TpaRequest request) {
        this.plugin = plugin;
        this.targetId = request.target();
        this.requestId = request.id();
        this.requesterName = request.requesterName();
        this.isTpaHere = request.direction() == TpaRequest.Direction.TPAHERE;
    }

    /** @return true if the form was actually handed to the companion for delivery. */
    public boolean open() {
        if (!Integrations.GEYSER_MENU_API) {
            return false;
        }
        GeyserMenuAPI api = GeyserMenuAPI.getInstance();
        if (api == null) {
            plugin.getLogger().warning("GeyserMenuAPI not available - falling back to chat for "
                    + targetId + "'s incoming TPA request.");
            return false;
        }

        String content = isTpaHere
                ? "§b" + requesterName + "§r wants you to teleport to them.\n\n§7Do you accept this request?"
                : "§b" + requesterName + "§r wants to teleport to you.\n\n§7Do you accept this request?";

        try {
            api.createModalMenu("§lTeleport Request", targetId)
                    .content(content)
                    .button("§a✔ Accept")
                    .button("§c✖ Deny")
                    .send(response -> {
                        // ---- companion network thread from here on ----
                        if (response.wasClosed()) {
                            // Closing the form leaves the request pending; it still expires on its
                            // own and can still be answered with /tpaccept.
                            Chat.send(plugin, targetId,
                                    plugin.getMessages().get("request-ignored", "player", requesterName));
                            return;
                        }
                        plugin.getTpaManager().resolveById(targetId, requestId, response.getButtonId() == 0);
                    });
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to send TPA request form to " + targetId + ": " + t);
            return false;
        }
    }
}
