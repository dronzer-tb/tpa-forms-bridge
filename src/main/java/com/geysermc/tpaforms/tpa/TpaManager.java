package com.geysermc.tpaforms.tpa;

import com.geysermc.tpaforms.TPAFormsPlugin;
import com.geysermc.tpaforms.config.Messages;
import com.geysermc.tpaforms.config.PlayerSettings;
import com.geysermc.tpaforms.menu.TpaRequestForm;
import com.geysermc.tpaforms.util.Chat;
import com.geysermc.tpaforms.util.Schedulers;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * The standalone TPA engine. This plugin no longer shells out to Essentials for any part of the
 * flow - request creation, queueing, expiry, warmup, cooldown and the teleport itself all live
 * here.
 *
 * <h2>Store</h2>
 * {@link #incoming} maps <em>target UUID -&gt; list of pending requests aimed at that target</em>.
 * A target can legitimately have several (that is what {@code /tpaqueue} and the optional
 * {@code /tpaccept <player>} argument exist for), so the value is a
 * {@link CopyOnWriteArrayList}: mutations are rare (a request arriving or resolving), iteration is
 * frequent (the expiry sweep, queue listing, name lookups) and must never throw
 * {@code ConcurrentModificationException} when a form callback resolves a request mid-sweep.
 * Outgoing requests are not separately indexed - they are found by scanning, which is trivially
 * cheap at realistic player counts and removes a whole class of index-desync bugs.
 *
 * <h2>Folia contract</h2>
 * <ul>
 *   <li>Nothing here ever stores a {@link Player}. Requests hold UUIDs plus cached names; every
 *       {@code Bukkit.getPlayer}/{@code getOnlinePlayers} lookup happens on the <b>global region
 *       thread</b> (see {@link #onGlobal}).</li>
 *   <li>The expiry sweep is a repeating task on the <b>global region scheduler</b>.</li>
 *   <li>A warmup is a repeating task on the moving player's <b>entity scheduler</b>, so it follows
 *       them across region and world boundaries and can legally read their location each tick.</li>
 *   <li>The teleport is {@link Player#teleportAsync(Location)} issued from the moving player's own
 *       entity scheduler, with the destination location snapshotted on the <em>destination</em>
 *       player's entity thread first. The returned future is always consumed.</li>
 *   <li>No {@code Bukkit.getScheduler()} / {@code BukkitRunnable} anywhere.</li>
 * </ul>
 */
public class TpaManager {

    /** How often a warmup re-checks movement. 4 checks/second keeps move-cancel responsive. */
    private static final long WARMUP_TICK_PERIOD = 5L;

    private final TPAFormsPlugin plugin;
    private final Messages messages;

    /** target UUID -> pending requests aimed at that target. */
    private final Map<UUID, CopyOnWriteArrayList<TpaRequest>> incoming = new ConcurrentHashMap<>();
    /** mover UUID -> epoch millis before which they may not teleport again. */
    private final Map<UUID, Long> cooldownUntil = new ConcurrentHashMap<>();
    /** mover UUID -> in-flight warmup. At most one per player. */
    private final Map<UUID, Warmup> warmups = new ConcurrentHashMap<>();

    private volatile ScheduledTask sweepTask;

    /**
     * Online player names, refreshed by the expiry sweep on the global region thread and read from
     * command tab-completion. Tab-completion has to answer synchronously on whichever thread the
     * command framework calls it from, so it must not read {@code Bukkit.getOnlinePlayers()}
     * itself; a snapshot at most one sweep old is more than fresh enough for a name list.
     */
    private volatile List<String> onlineNames = List.of();

    // --- config snapshot, replaced wholesale on reload -------------------------------------
    private volatile long timeoutMillis = 120_000L;
    private volatile int warmupSeconds = 0;
    private volatile boolean warmupCancelOnMove = true;
    private volatile boolean warmupCancelOnDamage = true;
    private volatile double warmupMoveThreshold = 0.5D;
    private volatile long cooldownMillis = 0L;
    private volatile long sweepPeriodTicks = 20L;
    private volatile boolean clickableMessages = true;
    private volatile boolean debug = false;

    public TpaManager(TPAFormsPlugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    // =====================================================================================
    // Lifecycle
    // =====================================================================================

    public void reloadSettings() {
        var config = plugin.getConfig();
        this.timeoutMillis = Math.max(5L, config.getLong("tpa.request-timeout-seconds", 120L)) * 1000L;
        this.warmupSeconds = Math.max(0, config.getInt("tpa.warmup-seconds", 0));
        this.warmupCancelOnMove = config.getBoolean("tpa.warmup-cancel-on-move", true);
        this.warmupCancelOnDamage = config.getBoolean("tpa.warmup-cancel-on-damage", true);
        this.warmupMoveThreshold = Math.max(0.05D, config.getDouble("tpa.warmup-move-threshold", 0.5D));
        this.cooldownMillis = Math.max(0L, config.getLong("tpa.cooldown-seconds", 0L)) * 1000L;
        this.sweepPeriodTicks = Math.max(5L, config.getLong("tpa.expiry-check-ticks", 20L));
        this.clickableMessages = config.getBoolean("tpa.clickable-messages", true);
        this.debug = config.getBoolean("debug", false);
    }

    /** Starts the expiry sweep on the global region scheduler. Call from onEnable. */
    public void start() {
        reloadSettings();
        this.sweepTask = Schedulers.globalRepeating(plugin, this::sweepExpired, sweepPeriodTicks, sweepPeriodTicks);
        plugin.getLogger().info("TPA engine started (timeout=" + (timeoutMillis / 1000) + "s, warmup="
                + warmupSeconds + "s, cooldown=" + (cooldownMillis / 1000) + "s).");
    }

    /** Cancels the sweep and every in-flight warmup. Call from onDisable. */
    public void stop() {
        ScheduledTask task = this.sweepTask;
        if (task != null) {
            task.cancel();
            this.sweepTask = null;
        }
        for (Warmup warmup : warmups.values()) {
            warmup.abort();
        }
        warmups.clear();
        incoming.clear();
        cooldownUntil.clear();
    }

    // =====================================================================================
    // Creating requests
    // =====================================================================================

    /**
     * Entry point for {@code /tpa} and {@code /tpahere}. Must be called on the global region
     * thread - it resolves the target by name out of the server player list.
     */
    public void createRequest(UUID requesterId, String requesterName, String targetName,
                              TpaRequest.Direction direction) {
        onGlobal(() -> {
            Player target = Bukkit.getPlayerExact(targetName);
            if (target == null || !target.isOnline()) {
                send(requesterId, messages.get("player-not-found", "player", targetName));
                return;
            }
            UUID targetId = target.getUniqueId();
            String resolvedTargetName = target.getName();

            if (targetId.equals(requesterId)) {
                send(requesterId, messages.get("self-request"));
                return;
            }

            PlayerSettings targetSettings = plugin.getSettingsManager().getSettings(targetId);
            if (!targetSettings.isAcceptingRequests()) {
                send(requesterId, messages.get("target-toggled-off", "player", resolvedTargetName));
                return;
            }
            if (targetSettings.isIgnored(requesterId)) {
                // Deliberately the same message as the toggle case: telling the requester they are
                // specifically on an ignore list turns a mute into harassment ammunition.
                send(requesterId, messages.get("target-ignores-you", "player", resolvedTargetName));
                return;
            }

            CopyOnWriteArrayList<TpaRequest> queue =
                    incoming.computeIfAbsent(targetId, key -> new CopyOnWriteArrayList<>());
            for (TpaRequest existing : queue) {
                if (existing.requester().equals(requesterId) && !existing.isResolved()) {
                    send(requesterId, messages.get("already-requested", "player", resolvedTargetName));
                    return;
                }
            }

            TpaRequest request = new TpaRequest(requesterId, requesterName, targetId,
                    resolvedTargetName, direction, timeoutMillis);
            queue.add(request);

            long seconds = request.secondsRemaining(System.currentTimeMillis());
            send(requesterId, messages.get(
                    direction == TpaRequest.Direction.TPAHERE ? "tpa-here-sent" : "tpa-sent",
                    "player", resolvedTargetName, "seconds", seconds));

            if (debug) {
                plugin.getLogger().info("TPA request created: " + request + " (id=" + request.id() + ")");
            }

            notifyTarget(request, targetSettings);
        });
    }

    /**
     * Delivers an incoming request to its target: auto-accept, then a Bedrock form if applicable,
     * then chat. Runs on the global region thread (called from {@link #createRequest}).
     *
     * <p>This is the path that was structurally impossible before: it used to depend on catching
     * EssentialsX's {@code TPARequestEvent}, which the installed "EssentialsC" plugin does not
     * publish, so a Bedrock player could never be shown an incoming-request form. Now the request
     * originates inside this plugin, so the form always fires.
     */
    private void notifyTarget(TpaRequest request, PlayerSettings targetSettings) {
        UUID targetId = request.target();

        // Auto-accept applies to plain TPA only, never TPAHERE (which would move the target).
        if (targetSettings.isAutoAcceptTpa() && request.direction() == TpaRequest.Direction.TPA) {
            resolve(request, true, targetId);
            return;
        }

        boolean bedrock = plugin.isBedrockPlayer(targetId);
        boolean formShown = false;
        if (bedrock && targetSettings.isFormsDelivery()) {
            formShown = new TpaRequestForm(plugin, request).open();
        }

        String line = messages.get(
                request.direction() == TpaRequest.Direction.TPAHERE ? "tpa-here-received" : "tpa-received",
                "player", request.requesterName());
        String hint = messages.get("tpa-received-hint",
                "player", request.requesterName(),
                "seconds", request.secondsRemaining(System.currentTimeMillis()));

        if (bedrock || !clickableMessages) {
            // Bedrock clients render click events unreliably; they get the form plus a plain hint.
            send(targetId, line);
            if (!formShown) {
                send(targetId, hint);
            }
        } else {
            Chat.sendAcceptDeny(plugin, targetId, line,
                    messages.get("accept-button"),
                    messages.get("accept-hover", "player", request.requesterName()),
                    "/tpaccept " + request.requesterName(),
                    messages.get("deny-button"),
                    messages.get("deny-hover", "player", request.requesterName()),
                    "/tpdeny " + request.requesterName(),
                    hint);
        }
    }

    // =====================================================================================
    // Resolving requests
    // =====================================================================================

    /** {@code /tpaccept [player]}. {@code requesterName} may be null for "the oldest one". */
    public void acceptRequest(UUID targetId, String requesterName) {
        onGlobal(() -> pickIncoming(targetId, requesterName).ifPresent(req -> resolve(req, true, targetId)));
    }

    /** {@code /tpdeny [player]}. {@code requesterName} may be null for "the oldest one". */
    public void denyRequest(UUID targetId, String requesterName) {
        onGlobal(() -> pickIncoming(targetId, requesterName).ifPresent(req -> resolve(req, false, targetId)));
    }

    /**
     * Accept/deny a specific request by id. Used by the Bedrock form callback, which arrives on
     * the GeyserMenuCompanion network thread long after the form was sent - by then the request
     * may already have expired or been answered in chat, so the id is re-looked-up rather than
     * trusted.
     */
    public void resolveById(UUID targetId, UUID requestId, boolean accept) {
        onGlobal(() -> {
            CopyOnWriteArrayList<TpaRequest> queue = incoming.get(targetId);
            if (queue == null) {
                return;
            }
            for (TpaRequest request : queue) {
                if (request.id().equals(requestId)) {
                    resolve(request, accept, targetId);
                    return;
                }
            }
            // Silently ignore: the request is gone (expired/answered), and the player has already
            // been told about that by the expiry or resolution message.
        });
    }

    /**
     * Common accept/deny tail. {@code actor} is whoever caused this so we do not echo a redundant
     * message back at them. Global region thread.
     */
    private void resolve(TpaRequest request, boolean accept, UUID actor) {
        UUID moverId = request.mover();

        // Cooldown is checked BEFORE claiming the request. claim() is a one-way latch, so a
        // cooldown rejection after it would consume the request and leave the player with nothing
        // to re-accept once the cooldown elapsed. This way the request simply stays pending.
        if (accept) {
            long now = System.currentTimeMillis();
            Long until = cooldownUntil.get(moverId);
            if (until != null && until > now && !hasBypass(moverId, "tpaforms.bypass.cooldown")) {
                String notice = messages.get("cooldown", "seconds", (until - now + 999L) / 1000L);
                // The mover is not always the actor: for /tpahere the target moves, and for an
                // auto-accepted /tpa the requester moves while the target is nominally the actor.
                // Both need to know why nothing happened.
                send(moverId, notice);
                if (actor != null && !actor.equals(moverId)) {
                    send(actor, notice);
                }
                return;
            }
        }

        if (!request.claim()) {
            return; // another thread (expiry sweep, second click, chat command) already won
        }
        removeFromQueue(request);

        if (!accept) {
            send(request.target(), messages.get("denied-by-target", "player", request.requesterName()));
            send(request.requester(), messages.get("denied-by-requester", "player", request.targetName()));
            return;
        }

        send(request.target(), messages.get("accepted-by-target", "player", request.requesterName()));
        send(request.requester(), messages.get("accepted-by-requester", "player", request.targetName()));

        if (warmupSeconds > 0 && !hasBypass(moverId, "tpaforms.bypass.warmup")) {
            startWarmup(request);
        } else {
            performTeleport(request);
        }
    }

    /** Finds the request to act on, messaging the player when the choice is empty or ambiguous. */
    private java.util.Optional<TpaRequest> pickIncoming(UUID targetId, String requesterName) {
        CopyOnWriteArrayList<TpaRequest> queue = incoming.get(targetId);
        List<TpaRequest> live = new ArrayList<>();
        if (queue != null) {
            for (TpaRequest request : queue) {
                if (!request.isResolved()) {
                    live.add(request);
                }
            }
        }
        if (live.isEmpty()) {
            send(targetId, messages.get("no-pending"));
            return java.util.Optional.empty();
        }
        if (requesterName != null) {
            for (TpaRequest request : live) {
                if (request.requesterName().equalsIgnoreCase(requesterName)) {
                    return java.util.Optional.of(request);
                }
            }
            send(targetId, messages.get("no-pending-from", "player", requesterName));
            return java.util.Optional.empty();
        }
        if (live.size() > 1) {
            send(targetId, messages.get("ambiguous-request", "count", live.size()));
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(live.get(0));
    }

    /** {@code /tpcancel [player]} - cancels the sender's OUTGOING request(s). */
    public void cancelRequest(UUID requesterId, String targetName) {
        onGlobal(() -> {
            List<TpaRequest> outgoing = outgoingOf(requesterId);
            if (outgoing.isEmpty()) {
                send(requesterId, messages.get("no-outgoing"));
                return;
            }
            if (targetName == null) {
                for (TpaRequest request : outgoing) {
                    cancelOne(request);
                }
                return;
            }
            for (TpaRequest request : outgoing) {
                if (request.targetName().equalsIgnoreCase(targetName)) {
                    cancelOne(request);
                    return;
                }
            }
            send(requesterId, messages.get("no-outgoing"));
        });
    }

    private void cancelOne(TpaRequest request) {
        if (!request.claim()) {
            return;
        }
        removeFromQueue(request);
        send(request.requester(), messages.get("tpa-cancelled", "player", request.targetName()));
        send(request.target(), messages.get("tpa-cancelled-target", "player", request.requesterName()));
    }

    // =====================================================================================
    // Queue listing
    // =====================================================================================

    /** {@code /tpaqueue} - shows the sender's incoming and outgoing pending requests. */
    public void showQueue(UUID playerId) {
        onGlobal(() -> {
            long now = System.currentTimeMillis();
            List<TpaRequest> in = new ArrayList<>();
            CopyOnWriteArrayList<TpaRequest> queue = incoming.get(playerId);
            if (queue != null) {
                for (TpaRequest request : queue) {
                    if (!request.isResolved()) {
                        in.add(request);
                    }
                }
            }
            in.sort(Comparator.comparingLong(TpaRequest::createdAtMillis));
            List<TpaRequest> out = outgoingOf(playerId);

            if (in.isEmpty() && out.isEmpty()) {
                send(playerId, messages.get("queue-empty"));
                return;
            }
            if (!in.isEmpty()) {
                send(playerId, messages.get("queue-header", "count", in.size()));
                for (TpaRequest request : in) {
                    send(playerId, messages.get("queue-entry",
                            "player", request.requesterName(),
                            "type", request.direction() == TpaRequest.Direction.TPAHERE ? "tpahere" : "tpa",
                            "seconds", request.secondsRemaining(now)));
                }
            }
            if (!out.isEmpty()) {
                send(playerId, messages.get("queue-outgoing-header", "count", out.size()));
                for (TpaRequest request : out) {
                    send(playerId, messages.get("queue-entry",
                            "player", request.targetName(),
                            "type", request.direction() == TpaRequest.Direction.TPAHERE ? "tpahere" : "tpa",
                            "seconds", request.secondsRemaining(now)));
                }
            }
        });
    }

    /**
     * Snapshot of the live incoming requests for a player, oldest first.
     *
     * <p>Safe to call from any thread - it reads only this plugin's own concurrent collections and
     * the immutable request objects, never the Bukkit API - which is what lets the Bedrock
     * "pending requests" form build its button list on the companion network thread.
     */
    public List<TpaRequest> pendingFor(UUID targetId) {
        CopyOnWriteArrayList<TpaRequest> queue = incoming.get(targetId);
        if (queue == null) {
            return List.of();
        }
        List<TpaRequest> live = new ArrayList<>();
        for (TpaRequest request : queue) {
            if (!request.isResolved()) {
                live.add(request);
            }
        }
        live.sort(Comparator.comparingLong(TpaRequest::createdAtMillis));
        return live;
    }

    private List<TpaRequest> outgoingOf(UUID requesterId) {
        List<TpaRequest> result = new ArrayList<>();
        for (CopyOnWriteArrayList<TpaRequest> queue : incoming.values()) {
            for (TpaRequest request : queue) {
                if (!request.isResolved() && request.requester().equals(requesterId)) {
                    result.add(request);
                }
            }
        }
        result.sort(Comparator.comparingLong(TpaRequest::createdAtMillis));
        return result;
    }

    // =====================================================================================
    // Expiry
    // =====================================================================================

    /**
     * Runs every {@code tpa.expiry-check-ticks} on the global region scheduler. Only touches this
     * plugin's own maps and sends chat, so the global region is the right (and cheapest) home for
     * it - it must not be a region task, because the requests it walks belong to players spread
     * across every region on the server.
     */
    private void sweepExpired() {
        long now = System.currentTimeMillis();
        refreshOnlineNames();
        for (Map.Entry<UUID, CopyOnWriteArrayList<TpaRequest>> entry : incoming.entrySet()) {
            CopyOnWriteArrayList<TpaRequest> queue = entry.getValue();
            for (TpaRequest request : queue) {
                if (request.isResolved()) {
                    queue.remove(request);
                    continue;
                }
                if (request.isExpired(now) && request.claim()) {
                    queue.remove(request);
                    send(request.target(), messages.get("expired-target", "player", request.requesterName()));
                    send(request.requester(), messages.get("expired-requester", "player", request.targetName()));
                }
            }
            // Drop the bucket once it is empty so the map does not grow one permanent entry per
            // player who has ever received a request. computeIfPresent keeps the map mutation
            // atomic; the only code that adds to a bucket (createRequest) also runs on this same
            // global region thread, so there is no window where a fresh request lands in a list
            // that is about to be unmapped.
            incoming.computeIfPresent(entry.getKey(), (key, value) -> value.isEmpty() ? null : value);
        }
    }

    /** Global region thread (called from the sweep). */
    private void refreshOnlineNames() {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        this.onlineNames = List.copyOf(names);
    }

    /** Snapshot of online player names for tab-completion. Safe to read from any thread. */
    public List<String> cachedOnlineNames() {
        return onlineNames;
    }

    private void removeFromQueue(TpaRequest request) {
        CopyOnWriteArrayList<TpaRequest> queue = incoming.get(request.target());
        if (queue != null) {
            queue.remove(request);
        }
    }

    /** Drops everything involving a player who just left, and kills their warmup. */
    public void handleQuit(UUID uuid) {
        cancelWarmup(uuid, null);

        // Requests aimed at the leaver: tell each requester rather than letting the request
        // vanish and look like it was ignored.
        CopyOnWriteArrayList<TpaRequest> theirs = incoming.remove(uuid);
        if (theirs != null) {
            for (TpaRequest request : theirs) {
                if (request.claim()) {
                    send(request.requester(),
                            messages.get("teleport-target-gone", "player", request.targetName()));
                }
            }
        }

        // Requests the leaver sent: drop them silently, the target loses nothing.
        for (CopyOnWriteArrayList<TpaRequest> queue : incoming.values()) {
            for (TpaRequest request : queue) {
                if (request.requester().equals(uuid) && request.claim()) {
                    queue.remove(request);
                }
            }
        }
        cooldownUntil.remove(uuid);
    }

    // =====================================================================================
    // Warmup
    // =====================================================================================

    /**
     * One pending warmup. The mutable {@code elapsedTicks}/{@code anchor} fields are only ever
     * touched from the mover's entity-scheduler callback (a single thread at a time, with the
     * happens-before edge supplied by the scheduler), while {@code cancelled} and {@code task} are
     * written from other threads (the damage listener, quit handler, onDisable) and so are
     * concurrency-safe types.
     */
    private final class Warmup {
        private final TpaRequest request;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private volatile ScheduledTask task;
        private final int totalTicks;
        private int elapsedTicks;
        private Location anchor;

        private Warmup(TpaRequest request, int totalTicks) {
            this.request = request;
            this.totalTicks = totalTicks;
        }

        private void abort() {
            cancelled.set(true);
            ScheduledTask current = task;
            if (current != null) {
                current.cancel();
            }
        }
    }

    private void startWarmup(TpaRequest request) {
        UUID moverId = request.mover();
        onGlobal(() -> {
            Player mover = Bukkit.getPlayer(moverId);
            if (mover == null) {
                send(request.destination(), messages.get("teleport-target-gone"));
                return;
            }
            Warmup existing = warmups.remove(moverId);
            if (existing != null) {
                existing.abort();
            }

            Warmup warmup = new Warmup(request, warmupSeconds * 20);
            warmups.put(moverId, warmup);
            send(moverId, messages.get("warmup-start", "seconds", warmupSeconds));

            // Entity scheduler, NOT region scheduler: the task must migrate with the player if they
            // cross a region/world boundary during the countdown, otherwise the callback would be
            // reading a location owned by a thread it is no longer running on.
            ScheduledTask task = Schedulers.onEntityRepeating(plugin, mover,
                    scheduled -> tickWarmup(moverId, warmup, mover, scheduled),
                    () -> warmups.remove(moverId, warmup),
                    WARMUP_TICK_PERIOD, WARMUP_TICK_PERIOD);

            if (task == null) {
                // Entity already retired between the lookup and the schedule call.
                warmups.remove(moverId, warmup);
                return;
            }
            warmup.task = task;
            // The task may have started before the field assignment above; honour a cancel that
            // landed in that window.
            if (warmup.cancelled.get()) {
                task.cancel();
            }
        });
    }

    /** Runs on the mover's own entity/region thread - reading their location here is legal. */
    private void tickWarmup(UUID moverId, Warmup warmup, Player mover, ScheduledTask task) {
        if (warmup.cancelled.get() || !mover.isOnline()) {
            task.cancel();
            warmups.remove(moverId, warmup);
            return;
        }
        Location current = mover.getLocation();
        if (warmup.anchor == null) {
            warmup.anchor = current.clone();
        } else if (warmupCancelOnMove && hasMoved(warmup.anchor, current)) {
            task.cancel();
            warmups.remove(moverId, warmup);
            send(moverId, messages.get("warmup-cancelled-move"));
            return;
        }

        warmup.elapsedTicks += WARMUP_TICK_PERIOD;
        if (warmup.elapsedTicks >= warmup.totalTicks) {
            task.cancel();
            warmups.remove(moverId, warmup);
            performTeleport(warmup.request);
        }
    }

    private boolean hasMoved(Location anchor, Location current) {
        if (anchor.getWorld() == null || current.getWorld() == null
                || !anchor.getWorld().getUID().equals(current.getWorld().getUID())) {
            return true;
        }
        return anchor.distanceSquared(current) > warmupMoveThreshold * warmupMoveThreshold;
    }

    /**
     * Cancels a warmup from outside its own thread (damage listener, quit handler).
     * {@code reasonKey} may be null to cancel silently.
     */
    public void cancelWarmup(UUID moverId, String reasonKey) {
        Warmup warmup = warmups.remove(moverId);
        if (warmup == null) {
            return;
        }
        warmup.abort();
        if (reasonKey != null) {
            send(moverId, messages.get(reasonKey));
        }
    }

    public boolean hasWarmup(UUID moverId) {
        return warmups.containsKey(moverId);
    }

    public boolean isCancelWarmupOnDamage() {
        return warmupCancelOnDamage;
    }

    // =====================================================================================
    // The teleport
    // =====================================================================================

    /**
     * Three deliberate thread hops, and every one of them is load-bearing on Folia:
     * <ol>
     *   <li><b>global region thread</b> - the only place the server player list may be read, so
     *       both UUIDs are resolved to {@link Player}s here;</li>
     *   <li><b>destination player's entity thread</b> - {@code getLocation()} reads region-owned
     *       state, so it must run on the thread that owns the destination player, which is very
     *       often NOT the mover's thread;</li>
     *   <li><b>mover's entity thread</b> - {@link Player#teleportAsync(Location)} must be issued
     *       from the thread that owns the entity being teleported.</li>
     * </ol>
     * Between hops only the immutable {@link Location} snapshot and plain object references travel;
     * calling {@code Entity#getScheduler()} on a foreign entity is explicitly thread-safe and is
     * the whole point of the entity scheduler API.
     *
     * <p>{@code teleportAsync} is the only teleport call in this plugin. The synchronous
     * {@code Player#teleport} throws on Folia for any cross-region destination, which is exactly
     * what a TPA does.
     */
    private void performTeleport(TpaRequest request) {
        UUID moverId = request.mover();
        UUID destinationId = request.destination();

        onGlobal(() -> {
            Player mover = Bukkit.getPlayer(moverId);
            Player destination = Bukkit.getPlayer(destinationId);
            if (mover == null) {
                send(destinationId, messages.get("teleport-target-gone"));
                return;
            }
            if (destination == null) {
                send(moverId, messages.get("teleport-target-gone"));
                return;
            }

            // Hop 2: snapshot the destination on ITS owning thread.
            boolean scheduled = Schedulers.onEntity(plugin, destination, () -> {
                Location snapshot = destination.getLocation().clone();

                // Hop 3: perform the teleport on the MOVER's owning thread.
                boolean moverScheduled = Schedulers.onEntity(plugin, mover,
                        () -> teleportNow(mover, moverId, destinationId, snapshot));
                if (!moverScheduled) {
                    send(moverId, messages.get("teleport-failed"));
                }
            });
            if (!scheduled) {
                send(moverId, messages.get("teleport-target-gone"));
            }
        });
    }

    /** Runs on the mover's own entity/region thread. */
    private void teleportNow(Player mover, UUID moverId, UUID destinationId, Location destination) {
        if (!mover.isOnline()) {
            return;
        }
        send(moverId, messages.get("teleporting"));
        // The future is always consumed - an unhandled teleportAsync failure would otherwise be a
        // silent no-op that looks to the player like the plugin ate their teleport.
        mover.teleportAsync(destination).whenComplete((success, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.WARNING,
                        "Teleport of " + moverId + " to " + destinationId + " failed", error);
                send(moverId, messages.get("teleport-failed"));
                return;
            }
            if (Boolean.FALSE.equals(success)) {
                plugin.getLogger().warning("Teleport of " + moverId + " to " + destinationId
                        + " was refused by the server (teleportAsync returned false).");
                send(moverId, messages.get("teleport-failed"));
                return;
            }
            if (cooldownMillis > 0L) {
                cooldownUntil.put(moverId, System.currentTimeMillis() + cooldownMillis);
            }
        });
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    /**
     * Runs {@code action} on the global region thread. Everything public on this class funnels
     * through here because callers arrive from three different kinds of thread: a player's region
     * thread (chat commands), the GeyserMenuCompanion network thread (form callbacks) and the
     * global region thread itself (the expiry sweep).
     */
    private void onGlobal(Runnable action) {
        Schedulers.global(plugin, action);
    }

    /** Global region thread only. */
    private boolean hasBypass(UUID uuid, String permission) {
        Player player = Bukkit.getPlayer(uuid);
        return player != null && player.hasPermission(permission);
    }

    private void send(UUID uuid, String message) {
        Chat.send(plugin, uuid, message);
    }

    public Messages messages() {
        return messages;
    }
}
