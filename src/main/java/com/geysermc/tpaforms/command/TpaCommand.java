package com.geysermc.tpaforms.command;

import com.geysermc.tpaforms.TPAFormsPlugin;
import com.geysermc.tpaforms.config.Messages;
import com.geysermc.tpaforms.config.PlayerSettings;
import com.geysermc.tpaforms.tpa.TpaRequest;
import com.geysermc.tpaforms.util.Schedulers;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Single executor backing all eight TPA commands, dispatched on the command label.
 *
 * <h2>Folia</h2>
 * A command runs on the region thread that owns the sender, which is NOT the global region thread
 * and therefore may not read the server player list. Every handler here immediately captures the
 * sender's UUID and name and hands off; {@link com.geysermc.tpaforms.tpa.TpaManager} funnels all of
 * its work onto the global region scheduler, and the few lookups this class does itself
 * ({@code /tpaignore}) are explicitly wrapped in {@link Schedulers#global}.
 *
 * <p>Tab-completion answers from {@code TpaManager}'s cached name snapshot rather than calling
 * {@code Bukkit.getOnlinePlayers()} inline, for the same reason.
 */
public class TpaCommand implements CommandExecutor, TabCompleter {

    private final TPAFormsPlugin plugin;
    private final Messages messages;

    public TpaCommand(TPAFormsPlugin plugin) {
        this.plugin = plugin;
        this.messages = plugin.getMessages();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("player-only"));
            return true;
        }
        UUID senderId = player.getUniqueId();
        String senderName = player.getName();
        String name = command.getName().toLowerCase(Locale.ROOT);

        // Bukkit already enforces the `permission:` declared in plugin.yml before dispatching, so
        // this is belt-and-braces - it exists so the configurable no-permission message is what
        // players see if the node is ever driven from somewhere other than the yml, and so a
        // programmatic dispatch cannot bypass the check.
        String permission = "tpaforms.command." + name;
        if (!player.hasPermission(permission)) {
            sender.sendMessage(messages.get("no-permission"));
            return true;
        }

        switch (name) {
            case "tpa" -> {
                if (args.length != 1) {
                    return usage(sender, "/tpa <player>");
                }
                plugin.getTpaManager().createRequest(senderId, senderName, args[0], TpaRequest.Direction.TPA);
            }
            case "tpahere" -> {
                if (args.length != 1) {
                    return usage(sender, "/tpahere <player>");
                }
                plugin.getTpaManager().createRequest(senderId, senderName, args[0], TpaRequest.Direction.TPAHERE);
            }
            case "tpaccept" -> plugin.getTpaManager()
                    .acceptRequest(senderId, args.length > 0 ? args[0] : null);
            case "tpdeny" -> plugin.getTpaManager()
                    .denyRequest(senderId, args.length > 0 ? args[0] : null);
            case "tpcancel" -> plugin.getTpaManager()
                    .cancelRequest(senderId, args.length > 0 ? args[0] : null);
            case "tpaqueue" -> plugin.getTpaManager().showQueue(senderId);
            case "tpatoggle" -> toggle(senderId);
            case "tpaignore" -> {
                if (args.length != 1) {
                    return usage(sender, "/tpaignore <player>");
                }
                ignore(senderId, args[0]);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private boolean usage(CommandSender sender, String usage) {
        sender.sendMessage(messages.get("usage", "usage", usage));
        return true;
    }

    private void toggle(UUID senderId) {
        // The settings object itself is thread-safe, but the confirmation message needs a player
        // lookup, so the whole thing goes to the global region thread for consistency.
        Schedulers.global(plugin, () -> {
            PlayerSettings settings = plugin.getSettingsManager().getSettings(senderId);
            boolean now = !settings.isAcceptingRequests();
            settings.setAcceptingRequests(now);
            plugin.getSettingsManager().saveSettingsAsync(senderId);
            Player player = Bukkit.getPlayer(senderId);
            if (player != null) {
                player.sendMessage(messages.get(now ? "toggle-on" : "toggle-off"));
            }
        });
    }

    private void ignore(UUID senderId, String targetName) {
        Schedulers.global(plugin, () -> {
            Player target = Bukkit.getPlayerExact(targetName);
            Player sender = Bukkit.getPlayer(senderId);
            if (target == null) {
                // Deliberately online-only: resolving an offline name means either a blocking
                // Mojang HTTP call or a usercache hit, and neither belongs on a region thread.
                if (sender != null) {
                    sender.sendMessage(messages.get("player-not-found", "player", targetName));
                }
                return;
            }
            PlayerSettings settings = plugin.getSettingsManager().getSettings(senderId);
            boolean nowIgnored = settings.toggleIgnored(target.getUniqueId());
            plugin.getSettingsManager().saveSettingsAsync(senderId);
            if (sender != null) {
                sender.sendMessage(messages.get(nowIgnored ? "ignore-added" : "ignore-removed",
                        "player", target.getName()));
            }
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        String self = player.getName();
        List<String> out = new ArrayList<>();
        for (String name : plugin.getTpaManager().cachedOnlineNames()) {
            if (!name.equalsIgnoreCase(self) && name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(name);
            }
        }
        return out;
    }
}
