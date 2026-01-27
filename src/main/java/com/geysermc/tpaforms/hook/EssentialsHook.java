package com.geysermc.tpaforms.hook;

import com.geysermc.tpaforms.TPAFormsPlugin;
import com.geysermc.tpaforms.config.PlayerSettings;
import com.geysermc.tpaforms.menu.TpaRequestForm;
import net.ess3.api.events.TPARequestEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Hook for EssentialsX TPA events.
 * Intercepts TPA requests and shows forms to Bedrock players.
 */
public class EssentialsHook implements Listener {

    private final TPAFormsPlugin plugin;

    public EssentialsHook(TPAFormsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Listen for TPA requests and show a form to Bedrock players.
     * If auto-accept is enabled for the target player (and it's not a tpahere request),
     * automatically accept the request.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTPARequest(TPARequestEvent event) {
        Player target = event.getTarget().getBase();
        
        // Get the requester player
        if (!event.getRequester().isPlayer()) {
            return; // Console TPA requests aren't handled
        }
        
        Player requester = event.getRequester().getPlayer();
        if (requester == null) {
            return;
        }

        boolean isTpaHere = event.isTeleportHere();
        
        // Only handle Bedrock players
        if (!plugin.getFloodgateHook().isBedrockPlayer(target)) {
            return;
        }

        plugin.getLogger().info("TPA request detected: " + requester.getName() + " -> " + target.getName() + 
                " (tpahere: " + isTpaHere + ")");

        PlayerSettings settings = plugin.getSettingsManager().getSettings(target.getUniqueId());

        // Check auto-accept for regular TPA requests (not tpahere)
        if (settings.isAutoAcceptTpa() && !isTpaHere) {
            plugin.getLogger().info("Auto-accepting TPA request for " + target.getName());
            // Execute tpaccept command on behalf of the target player
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                target.performCommand("tpaccept");
            });
            return;
        }

        // Check if forms delivery is enabled
        if (settings.isFormsDelivery()) {
            // Show the TPA request form to the Bedrock player
            new TpaRequestForm(plugin, target, requester, isTpaHere).open();
        }
        // If forms delivery is disabled, the default EssentialsX chat message will show
    }
}
