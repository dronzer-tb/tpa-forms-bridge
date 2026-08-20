package com.geysermc.tpaforms.config;

import org.bukkit.ChatColor;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every user-facing string, resolved from {@code config.yml} under {@code messages:} with a
 * built-in default so an out-of-date config never produces a null message.
 *
 * <p>Thread-safety: the backing map is replaced wholesale on {@link #reload(Plugin)} and the field
 * is volatile, so readers on any region thread always see a complete, consistent table.
 */
public final class Messages {

    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();

    static {
        // --- outgoing -----------------------------------------------------------------
        DEFAULTS.put("tpa-sent", "&aSent a teleport request to &b%player%&a. &7(expires in %seconds%s)");
        DEFAULTS.put("tpa-here-sent", "&aAsked &b%player%&a to teleport to you. &7(expires in %seconds%s)");
        DEFAULTS.put("tpa-cancelled", "&7Cancelled your teleport request to &b%player%&7.");
        DEFAULTS.put("tpa-cancelled-target", "&7&b%player%&7 cancelled their teleport request.");
        DEFAULTS.put("no-outgoing", "&cYou have no outgoing teleport requests.");
        DEFAULTS.put("already-requested", "&cYou already have a pending request to &b%player%&c.");
        DEFAULTS.put("self-request", "&cYou cannot send a teleport request to yourself.");
        DEFAULTS.put("player-not-found", "&cPlayer &b%player%&c is not online.");
        DEFAULTS.put("player-only", "&cThis command can only be used by a player.");
        DEFAULTS.put("no-permission", "&cYou do not have permission to do that.");
        DEFAULTS.put("usage", "&cUsage: &7%usage%");

        // --- incoming -----------------------------------------------------------------
        DEFAULTS.put("tpa-received", "&b%player% &fwants to teleport to you.");
        DEFAULTS.put("tpa-here-received", "&b%player% &fwants you to teleport to them.");
        DEFAULTS.put("tpa-received-hint", "&7Use &a/tpaccept %player% &7or &c/tpdeny %player%&7. Expires in %seconds%s.");
        DEFAULTS.put("accept-button", "&a&l[ACCEPT]");
        DEFAULTS.put("deny-button", "&c&l[DENY]");
        DEFAULTS.put("accept-hover", "&aAccept the request from %player%");
        DEFAULTS.put("deny-hover", "&cDeny the request from %player%");

        // --- resolution ---------------------------------------------------------------
        DEFAULTS.put("accepted-by-target", "&aAccepted the teleport request from &b%player%&a.");
        DEFAULTS.put("accepted-by-requester", "&b%player% &aaccepted your teleport request.");
        DEFAULTS.put("denied-by-target", "&7Denied the teleport request from &b%player%&7.");
        DEFAULTS.put("denied-by-requester", "&b%player% &cdenied your teleport request.");
        DEFAULTS.put("no-pending", "&cYou have no pending teleport requests.");
        DEFAULTS.put("no-pending-from", "&cYou have no pending teleport request from &b%player%&c.");
        DEFAULTS.put("ambiguous-request", "&eYou have %count% pending requests. Use &7/tpaccept <player>&e or &7/tpaqueue&e.");
        DEFAULTS.put("expired-target", "&7The teleport request from &b%player%&7 expired.");
        DEFAULTS.put("expired-requester", "&7Your teleport request to &b%player%&7 expired.");
        DEFAULTS.put("request-ignored", "&7Teleport request from &b%player%&7 was ignored.");

        // --- blocks -------------------------------------------------------------------
        DEFAULTS.put("target-toggled-off", "&b%player% &cis not accepting teleport requests.");
        DEFAULTS.put("target-ignores-you", "&b%player% &cis not accepting teleport requests.");
        DEFAULTS.put("toggle-on", "&aYou will now receive teleport requests.");
        DEFAULTS.put("toggle-off", "&cYou will no longer receive teleport requests.");
        DEFAULTS.put("ignore-added", "&cNow ignoring teleport requests from &b%player%&c.");
        DEFAULTS.put("ignore-removed", "&aNo longer ignoring teleport requests from &b%player%&a.");

        // --- queue --------------------------------------------------------------------
        DEFAULTS.put("queue-header", "&bPending teleport requests (%count%):");
        DEFAULTS.put("queue-entry", "&7 - &b%player% &7(%type%, %seconds%s left)");
        DEFAULTS.put("queue-empty", "&7You have no pending teleport requests.");
        DEFAULTS.put("queue-outgoing-header", "&bYour outgoing requests (%count%):");

        // --- teleport -----------------------------------------------------------------
        DEFAULTS.put("warmup-start", "&eTeleporting in &b%seconds%&e seconds - do not move.");
        DEFAULTS.put("warmup-cancelled-move", "&cTeleport cancelled - you moved.");
        DEFAULTS.put("warmup-cancelled-damage", "&cTeleport cancelled - you took damage.");
        DEFAULTS.put("teleporting", "&aTeleporting...");
        DEFAULTS.put("teleport-failed", "&cThe teleport failed. Please try again.");
        DEFAULTS.put("teleport-target-gone", "&cThe other player is no longer online.");
        DEFAULTS.put("cooldown", "&cYou must wait &b%seconds%&c more seconds before teleporting again.");

        // --- misc ---------------------------------------------------------------------
        DEFAULTS.put("settings-updated", "&aTPA settings updated successfully.");
        DEFAULTS.put("no-players-online", "&7There are no other players online.");
    }

    private volatile Map<String, String> table = Map.copyOf(DEFAULTS);

    public void reload(Plugin plugin) {
        Map<String, String> next = new LinkedHashMap<>(DEFAULTS);
        var section = plugin.getConfig().getConfigurationSection("messages");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String value = section.getString(key);
                if (value != null) {
                    next.put(key, value);
                }
            }
        }
        this.table = Map.copyOf(next);
    }

    /** Raw (still legacy-ampersand-coded) template for a key. */
    public String raw(String key) {
        String value = table.get(key);
        return value != null ? value : DEFAULTS.getOrDefault(key, key);
    }

    /**
     * Formats a message: substitutes {@code %placeholder%} pairs then translates {@code &} colour
     * codes. Returns an empty string for a template deliberately blanked out in config, which
     * callers treat as "send nothing".
     */
    public String get(String key, Object... placeholders) {
        String value = raw(key);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            value = value.replace("%" + placeholders[i] + "%", String.valueOf(placeholders[i + 1]));
        }
        return ChatColor.translateAlternateColorCodes('&', value);
    }

    public boolean isBlank(String key) {
        return raw(key).isEmpty();
    }

    /** The full default table, written into config.yml comments / used by tests. */
    public static Map<String, String> defaults() {
        return Map.copyOf(DEFAULTS);
    }
}
