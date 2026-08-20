package com.geysermc.tpaforms.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Chat delivery helpers.
 *
 * <p><b>Folia:</b> every entry point resolves the recipient with {@code Bukkit.getPlayer} on the
 * <em>global region thread</em>. That matters because these are called from GeyserMenuCompanion
 * network-thread form callbacks and from teleport-future completions, neither of which is a region
 * thread. Sending the packet itself is thread-safe once the reference is in hand, but obtaining it
 * from the server's player list is not.
 *
 * <p>Clickable components use Adventure, which Paper/Folia bundles. Every use is wrapped so that a
 * server where the relevant Adventure class is missing (or a client that chokes on the component)
 * degrades to the plain legacy string rather than losing the message.
 */
public final class Chat {

    private Chat() {
    }

    /** Sends a legacy colour-coded line, resolving the recipient on the global region thread. */
    public static void send(Plugin plugin, UUID uuid, String legacyMessage) {
        if (uuid == null || legacyMessage == null || legacyMessage.isEmpty()) {
            return;
        }
        Schedulers.global(plugin, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(legacyMessage);
            }
        });
    }

    /**
     * Sends {@code line}, then a second line carrying clickable ACCEPT / DENY buttons that run the
     * given commands. Falls back to sending {@code fallback} when components are unavailable.
     */
    public static void sendAcceptDeny(Plugin plugin, UUID uuid,
                                      String line,
                                      String acceptLabel, String acceptHover, String acceptCommand,
                                      String denyLabel, String denyHover, String denyCommand,
                                      String fallback) {
        if (uuid == null) {
            return;
        }
        Schedulers.global(plugin, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                return;
            }
            if (!line.isEmpty()) {
                player.sendMessage(line);
            }
            try {
                LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();
                Component accept = legacy.deserialize(acceptLabel)
                        .clickEvent(ClickEvent.runCommand(acceptCommand))
                        .hoverEvent(HoverEvent.showText(legacy.deserialize(acceptHover)));
                Component deny = legacy.deserialize(denyLabel)
                        .clickEvent(ClickEvent.runCommand(denyCommand))
                        .hoverEvent(HoverEvent.showText(legacy.deserialize(denyHover)));
                player.sendMessage(Component.text().append(accept)
                        .append(Component.text("  "))
                        .append(deny)
                        .build());
            } catch (Throwable t) {
                // Adventure absent or a serializer change - never lose the prompt over cosmetics.
                if (fallback != null && !fallback.isEmpty()) {
                    player.sendMessage(fallback);
                }
            }
        });
    }
}
