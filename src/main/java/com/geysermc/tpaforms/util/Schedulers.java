package com.geysermc.tpaforms.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Folia scheduler helpers.
 *
 * <p>Folia runs many region threads concurrently and {@code Bukkit.getScheduler()} throws
 * {@link UnsupportedOperationException} there, so every deferred/scheduled unit of work in this
 * plugin goes through one of these. The APIs used below also exist on ordinary Paper (where they
 * simply dispatch to the main thread), so the same jar is valid on Paper/Purpur/Leaf too.
 *
 * <p>Rules of thumb applied throughout this plugin:
 * <ul>
 *   <li>Touching a specific player/entity -> {@link #onEntity(Plugin, Entity, Runnable)}.</li>
 *   <li>Touching a specific block/world location -> {@link #onRegion(Plugin, Location, Runnable)}.</li>
 *   <li>Touching global server state (player list, plugin manager) -> {@link #global(Plugin, Runnable)}.</li>
 *   <li>Blocking I/O -> {@link #async(Plugin, Runnable)}.</li>
 * </ul>
 */
public final class Schedulers {

    private Schedulers() {
    }

    /** Runs on the global region thread (server-wide state: online player list, weather, etc.). */
    public static void global(Plugin plugin, Runnable runnable) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, runnable);
    }

    /** Runs on the global region thread after {@code delayTicks} ticks. */
    public static void globalDelayed(Plugin plugin, Runnable runnable, long delayTicks) {
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> runnable.run(), Math.max(1L, delayTicks));
    }

    /**
     * Runs on the global region thread every {@code periodTicks} ticks, starting after
     * {@code delayTicks}. Used for the TPA request expiry sweep, which only ever touches the
     * plugin's own maps plus {@code Bukkit.getPlayer} lookups - both global-region-safe.
     *
     * @return the task handle so the caller can cancel it in onDisable.
     */
    public static ScheduledTask globalRepeating(Plugin plugin, Runnable runnable,
                                                long delayTicks, long periodTicks) {
        return Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                plugin, task -> runnable.run(), Math.max(1L, delayTicks), Math.max(1L, periodTicks));
    }

    /**
     * Runs on the region thread that owns {@code entity}. This is the ONLY legal way to touch an
     * entity you did not receive on the current region thread.
     *
     * @return false if the entity has been removed / retired and the task will never run.
     */
    public static boolean onEntity(Plugin plugin, Entity entity, Runnable runnable) {
        if (entity == null) {
            return false;
        }
        return entity.getScheduler().run(plugin, task -> runnable.run(), null) != null;
    }

    /**
     * Repeating task bound to {@code entity}'s <em>entity scheduler</em>. The critical property on
     * Folia is that the task follows the entity across region boundaries: if the player walks into
     * another region (or another world) mid-warmup, the ticks keep firing on whichever region
     * thread now owns them, so reading {@code player.getLocation()} inside the callback stays
     * legal. A plain region scheduler task pinned to the start location would not.
     *
     * @param retired run instead if the entity is removed/despawned before the task completes.
     * @return the task handle, or {@code null} if the entity was already retired.
     */
    public static ScheduledTask onEntityRepeating(Plugin plugin, Entity entity,
                                                  Consumer<ScheduledTask> callback, Runnable retired,
                                                  long delayTicks, long periodTicks) {
        if (entity == null) {
            return null;
        }
        return entity.getScheduler().runAtFixedRate(
                plugin, callback, retired, Math.max(1L, delayTicks), Math.max(1L, periodTicks));
    }

    /** Runs on the region thread that owns {@code location}. */
    public static void onRegion(Plugin plugin, Location location, Runnable runnable) {
        Bukkit.getRegionScheduler().execute(plugin, location, runnable);
    }

    /** Runs off any region thread. Use for file/network I/O. */
    public static void async(Plugin plugin, Runnable runnable) {
        Bukkit.getAsyncScheduler().runNow(plugin, task -> runnable.run());
    }

    /** Runs off any region thread after a delay. */
    public static void asyncDelayed(Plugin plugin, Runnable runnable, long delayMillis) {
        Bukkit.getAsyncScheduler().runDelayed(plugin, task -> runnable.run(), delayMillis, TimeUnit.MILLISECONDS);
    }

    /** Cancels every task this plugin still has queued on any Folia scheduler. */
    public static void cancelAll(Plugin plugin) {
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
        Bukkit.getAsyncScheduler().cancelTasks(plugin);
    }
}
