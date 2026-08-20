package com.geysermc.tpaforms.tpa;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One pending teleport request.
 *
 * <p><b>Folia rule enforced by this type:</b> it stores UUIDs and names only, never
 * {@link org.bukkit.entity.Player} references. A request outlives the tick that created it (by up
 * to the configured timeout) and is read from the expiry sweep on the global region thread, from
 * command handlers, and from GeyserMenuCompanion network-thread form callbacks. A live {@code
 * Player} handed between those threads would be a cross-region access; a UUID re-resolved on the
 * global region thread is not. The names are cached purely so messages can be composed without
 * resolving anybody.
 *
 * <p>All fields are immutable except {@link #resolved}, which is the single-winner latch that
 * makes accept/deny/cancel/expire race-free: whichever thread flips it first owns the outcome.
 */
public final class TpaRequest {

    /** Which way the teleport goes once accepted. */
    public enum Direction {
        /** {@code /tpa}: the requester teleports to the target. */
        TPA,
        /** {@code /tpahere}: the target teleports to the requester. */
        TPAHERE
    }

    private final UUID id = UUID.randomUUID();
    private final UUID requester;
    private final String requesterName;
    private final UUID target;
    private final String targetName;
    private final Direction direction;
    private final long createdAtMillis;
    private final long expiresAtMillis;
    private final AtomicBoolean resolved = new AtomicBoolean(false);

    public TpaRequest(UUID requester, String requesterName, UUID target, String targetName,
                      Direction direction, long timeoutMillis) {
        this.requester = requester;
        this.requesterName = requesterName;
        this.target = target;
        this.targetName = targetName;
        this.direction = direction;
        this.createdAtMillis = System.currentTimeMillis();
        this.expiresAtMillis = this.createdAtMillis + timeoutMillis;
    }

    public UUID id() {
        return id;
    }

    public UUID requester() {
        return requester;
    }

    public String requesterName() {
        return requesterName;
    }

    public UUID target() {
        return target;
    }

    public String targetName() {
        return targetName;
    }

    public Direction direction() {
        return direction;
    }

    public long createdAtMillis() {
        return createdAtMillis;
    }

    public long expiresAtMillis() {
        return expiresAtMillis;
    }

    public boolean isExpired(long nowMillis) {
        return nowMillis >= expiresAtMillis;
    }

    public long secondsRemaining(long nowMillis) {
        return Math.max(0L, (expiresAtMillis - nowMillis + 999L) / 1000L);
    }

    /** The player who actually moves when this request is accepted. */
    public UUID mover() {
        return direction == Direction.TPA ? requester : target;
    }

    /** The player who stays put and is teleported to. */
    public UUID destination() {
        return direction == Direction.TPA ? target : requester;
    }

    public String moverName() {
        return direction == Direction.TPA ? requesterName : targetName;
    }

    public String destinationName() {
        return direction == Direction.TPA ? targetName : requesterName;
    }

    /** @return true exactly once, for the caller that wins the race to finish this request. */
    public boolean claim() {
        return resolved.compareAndSet(false, true);
    }

    public boolean isResolved() {
        return resolved.get();
    }

    @Override
    public String toString() {
        return "TpaRequest{" + requesterName + " -> " + targetName + ", " + direction + '}';
    }
}
