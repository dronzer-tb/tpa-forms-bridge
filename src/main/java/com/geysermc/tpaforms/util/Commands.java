package com.geysermc.tpaforms.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Folia-safe "make this player run this command" helper.
 *
 * <p>On Folia a command must execute on the region thread that owns the player running it - the
 * command implementation is free to teleport them, edit their inventory, read blocks around them,
 * and all of that is illegal from any other thread. Every call site in this plugin originates
 * either on a foreign region thread (the TPA event, which runs on the requester's region) or on
 * the GeyserMenuCompanion network thread (form response callbacks), so none of them may call
 * {@code performCommand} inline.
 */
public final class Commands {

    private Commands() {
    }

    /** Dispatches {@code command} as {@code player} on that player's own region thread. */
    public static void runAs(Plugin plugin, Player player, String command) {
        if (player == null) {
            return;
        }
        boolean scheduled = Schedulers.onEntity(plugin, player, () -> {
            if (player.isOnline()) {
                player.performCommand(command);
            }
        });
        if (!scheduled) {
            plugin.getLogger().fine("Could not schedule '" + command + "' for "
                    + player.getName() + " - player is no longer resident on any region.");
        }
    }

    /**
     * Resolves a UUID to an online player and dispatches the command on their region thread.
     * Used from form callbacks, which must not hold on to {@link Player} references across the
     * network round-trip (the player may have quit, or moved region, in the meantime).
     */
    public static void runAs(Plugin plugin, UUID uuid, String command) {
        Schedulers.global(plugin, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                runAs(plugin, player, command);
            }
        });
    }

    /**
     * Sends a chat message to a player by UUID if they are still online.
     *
     * <p>Delegates to {@link Chat#send(Plugin, UUID, String)}, which resolves the recipient on the
     * global region thread. The previous inline {@code Bukkit.getPlayer(uuid)} here was called
     * straight from GeyserMenuCompanion form callbacks - i.e. off any region thread - which is an
     * illegal read of the server player list on Folia.
     */
    public static void message(Plugin plugin, UUID uuid, String message) {
        Chat.send(plugin, uuid, message);
    }
}
