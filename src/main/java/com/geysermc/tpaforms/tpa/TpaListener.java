package com.geysermc.tpaforms.tpa;

import com.geysermc.tpaforms.TPAFormsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Cancels an in-flight teleport warmup when the moving player takes damage.
 *
 * <p>Folia: {@link EntityDamageEvent} fires on the region thread that owns the damaged entity,
 * which is by definition the warmup's own thread. {@code cancelWarmup} only touches the concurrent
 * warmup map and the {@code ScheduledTask} handle, both of which are safe to touch from any
 * thread, so no hop is needed here.
 *
 * <p>Movement cancellation deliberately does NOT live here: polling the player's location from
 * inside the warmup's own entity-scheduler task is both cheaper than a {@code PlayerMoveEvent}
 * handler and immune to the cross-region trap of a move event that fires while the warmup task has
 * migrated to a different thread.
 */
public class TpaListener implements Listener {

    private final TPAFormsPlugin plugin;

    public TpaListener(TPAFormsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        TpaManager manager = plugin.getTpaManager();
        if (manager == null || !manager.isCancelWarmupOnDamage()) {
            return;
        }
        if (manager.hasWarmup(player.getUniqueId())) {
            manager.cancelWarmup(player.getUniqueId(), "warmup-cancelled-damage");
        }
    }
}
