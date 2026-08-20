package com.geysermc.tpaforms.menu;

import com.geysermc.tpaforms.TPAFormsPlugin;
import com.geysermc.tpaforms.tpa.TpaRequest;
import com.geysermc.tpaforms.util.Chat;
import com.geysermc.tpaforms.util.Integrations;
import com.geysermenu.companion.api.GeyserMenuAPI;

import java.util.List;
import java.util.UUID;

/**
 * Bedrock equivalent of {@code /tpaqueue}: lists the viewer's pending incoming requests and lets
 * them accept or deny each one.
 *
 * <p>Folia: the list comes from {@link com.geysermc.tpaforms.tpa.TpaManager#pendingFor(UUID)},
 * which reads only plugin-owned concurrent state, so it is safe to build here on the companion
 * network thread. The accept/deny decision is routed through {@code resolveById}, which hops to the
 * global region thread and re-validates the request before acting on it.
 */
public class TpaPendingForm {

    private final TPAFormsPlugin plugin;
    private final UUID viewerId;

    public TpaPendingForm(TPAFormsPlugin plugin, UUID viewerId) {
        this.plugin = plugin;
        this.viewerId = viewerId;
    }

    public void open() {
        if (!Integrations.GEYSER_MENU_API) {
            return;
        }
        GeyserMenuAPI api = GeyserMenuAPI.getInstance();
        if (api == null) {
            return;
        }

        List<TpaRequest> pending = plugin.getTpaManager().pendingFor(viewerId);
        if (pending.isEmpty()) {
            api.createSimpleMenu("§l§bPending Requests", viewerId)
                    .content(plugin.getMessages().get("queue-empty"))
                    .button("§cClose")
                    .send(response -> { });
            return;
        }

        long now = System.currentTimeMillis();
        var builder = api.createSimpleMenu("§l§bPending Requests", viewerId)
                .content("§7Tap a request to answer it:");
        for (TpaRequest request : pending) {
            String label = "§f" + request.requesterName()
                    + (request.direction() == TpaRequest.Direction.TPAHERE ? " §7(tpahere)" : " §7(tpa)")
                    + " §8- " + request.secondsRemaining(now) + "s";
            builder.button(label, plugin.getSkinsRestorerHook().getTextureUrl(request.requesterName()));
        }
        builder.send(response -> {
            if (response.wasClosed()) {
                return;
            }
            int index = response.getButtonId();
            if (index < 0 || index >= pending.size()) {
                return;
            }
            answer(api, pending.get(index));
        });
    }

    private void answer(GeyserMenuAPI api, TpaRequest request) {
        api.createModalMenu("§lTeleport Request", viewerId)
                .content("§b" + request.requesterName() + "§r "
                        + (request.direction() == TpaRequest.Direction.TPAHERE
                        ? "wants you to teleport to them." : "wants to teleport to you."))
                .button("§a✔ Accept")
                .button("§c✖ Deny")
                .send(response -> {
                    if (response.wasClosed()) {
                        Chat.send(plugin, viewerId,
                                plugin.getMessages().get("request-ignored", "player", request.requesterName()));
                        return;
                    }
                    plugin.getTpaManager().resolveById(viewerId, request.id(), response.getButtonId() == 0);
                });
    }
}
