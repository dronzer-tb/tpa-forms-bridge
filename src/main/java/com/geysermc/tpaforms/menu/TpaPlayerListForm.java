package com.geysermc.tpaforms.menu;

import com.geysermc.tpaforms.TPAFormsPlugin;
import com.geysermc.tpaforms.util.Commands;
import com.geysermc.tpaforms.util.Integrations;
import com.geysermc.tpaforms.util.Schedulers;
import com.geysermenu.companion.api.GeyserMenuAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Form that lists online players so the viewer can send TPA/TPAHere requests.
 *
 * <p>Folia notes:
 * <ul>
 *   <li>{@code Bukkit.getOnlinePlayers()} is global server state; on Folia it must be read from
 *       the global region thread, not from the companion network thread that delivered the click
 *       that opened this form. {@link #open()} therefore hops to the global region scheduler.</li>
 *   <li>The snapshot is immediately reduced to immutable {@code (uuid, name)} records. Retaining
 *       live {@link Player} objects in a list that outlives the tick - and is then dereferenced
 *       from a network thread after a Bedrock round-trip - is exactly the cross-region access
 *       Folia forbids.</li>
 * </ul>
 */
public class TpaPlayerListForm {

    /** Immutable, region-free snapshot of an online player. */
    private record PlayerRef(UUID uuid, String name) { }

    private final TPAFormsPlugin plugin;
    private final UUID viewerId;

    public TpaPlayerListForm(TPAFormsPlugin plugin, Player viewer) {
        this(plugin, viewer.getUniqueId());
    }

    public TpaPlayerListForm(TPAFormsPlugin plugin, UUID viewerId) {
        this.plugin = plugin;
        this.viewerId = viewerId;
    }

    public void open() {
        if (!Integrations.GEYSER_MENU_API) {
            return;
        }
        // Snapshot the player list on the global region thread, then build/send the form.
        Schedulers.global(plugin, () -> {
            List<PlayerRef> others = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.getUniqueId().equals(viewerId)) {
                    others.add(new PlayerRef(online.getUniqueId(), online.getName()));
                }
            }
            sendForm(List.copyOf(others));
        });
    }

    private void sendForm(List<PlayerRef> others) {
        GeyserMenuAPI api = GeyserMenuAPI.getInstance();
        if (api == null) {
            plugin.getLogger().warning("GeyserMenuAPI not available");
            return;
        }

        if (others.isEmpty()) {
            api.createSimpleMenu("§l§bTeleport", viewerId)
                    .content(plugin.getMessages().get("no-players-online"))
                    .button("§cClose")
                    .send(response -> { });
            return;
        }

        var menuBuilder = api.createSimpleMenu("§l§bTeleport", viewerId)
                .content("§7Select a player to send a teleport request:");

        for (PlayerRef ref : others) {
            menuBuilder.button("§f" + ref.name(), plugin.getSkinsRestorerHook().getTextureUrl(ref.name()));
        }

        menuBuilder.send(response -> {
            if (response.wasClosed()) {
                return;
            }
            int buttonId = response.getButtonId();
            if (buttonId >= 0 && buttonId < others.size()) {
                openActionMenu(others.get(buttonId));
            }
        });
    }

    /** Submenu: TPA / TPAHere / back. */
    private void openActionMenu(PlayerRef selected) {
        GeyserMenuAPI api = GeyserMenuAPI.getInstance();
        if (api == null) {
            return;
        }

        String skinUrl = plugin.getSkinsRestorerHook().getTextureUrl(selected.name());

        api.createSimpleMenu("§l§b" + selected.name(), viewerId)
                .content("§7What would you like to do?")
                .button("§a➡ Teleport to them (TPA)", skinUrl)
                .button("§e⬅ Teleport them to you (TPAHere)", skinUrl)
                .button("§8↩ Back to player list")
                .send(response -> {
                    if (response.wasClosed()) {
                        return;
                    }
                    switch (response.getButtonId()) {
                        // Folia: was Bukkit.getScheduler().runTask(...). Commands.runAs resolves the
                        // viewer on the global region thread and then runs the command on the
                        // viewer's own region thread.
                        case 0 -> {
                            Commands.runAs(plugin, viewerId, "tpa " + selected.name());
                            // The confirmation itself comes from TpaManager once the request is actually created;
                            // duplicating it here would double-message the player.
                        }
                        case 1 -> {
                            Commands.runAs(plugin, viewerId, "tpahere " + selected.name());
                            
                        }
                        case 2 -> open();
                        default -> { }
                    }
                });
    }
}
