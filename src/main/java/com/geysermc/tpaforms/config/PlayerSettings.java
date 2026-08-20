package com.geysermc.tpaforms.config;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Per-player toggles, persisted as one JSON document per player by {@link SettingsManager}.
 *
 * <p>Folia note: a single instance is read from region threads (command handlers on the global
 * region, the TPA engine) and written from a GeyserMenuCompanion network thread (form response
 * callbacks). The scalar fields are {@code volatile} so a write on one thread is visible to the
 * other; {@link #ignored} is a {@link CopyOnWriteArraySet} for the same reason. With the previous
 * Lombok {@code @Data} plain fields this was an unsynchronised cross-thread read.
 *
 * <p>Gson populates these fields reflectively, which can install a plain {@code HashSet} (or
 * {@code null}) over the concurrent one, so {@link #normalize()} must be called on every instance
 * that came off disk before it is published to other threads.
 */
public class PlayerSettings {

    private volatile boolean autoAcceptTpa = false;
    private volatile boolean formsDelivery = true;

    /** {@code /tpatoggle}: when false the player receives no incoming TPA requests at all. */
    private volatile boolean acceptingRequests = true;

    /** {@code /tpaignore}: UUID strings of players whose requests are silently dropped. */
    private Set<String> ignored = new CopyOnWriteArraySet<>();

    public boolean isAutoAcceptTpa() {
        return autoAcceptTpa;
    }

    public void setAutoAcceptTpa(boolean autoAcceptTpa) {
        this.autoAcceptTpa = autoAcceptTpa;
    }

    public boolean isFormsDelivery() {
        return formsDelivery;
    }

    public void setFormsDelivery(boolean formsDelivery) {
        this.formsDelivery = formsDelivery;
    }

    public boolean isAcceptingRequests() {
        return acceptingRequests;
    }

    public void setAcceptingRequests(boolean acceptingRequests) {
        this.acceptingRequests = acceptingRequests;
    }

    public boolean isIgnored(UUID uuid) {
        return uuid != null && ignored.contains(uuid.toString());
    }

    /** @return true if the player is now ignored, false if they were un-ignored. */
    public boolean toggleIgnored(UUID uuid) {
        String key = uuid.toString();
        if (ignored.remove(key)) {
            return false;
        }
        ignored.add(key);
        return true;
    }

    public Collection<String> getIgnored() {
        return List.copyOf(ignored);
    }

    /** Re-wraps whatever Gson deserialised into the concurrent collection this class promises. */
    public PlayerSettings normalize() {
        Set<String> current = this.ignored;
        this.ignored = current == null ? new CopyOnWriteArraySet<>() : new CopyOnWriteArraySet<>(current);
        return this;
    }

    @Override
    public String toString() {
        return "PlayerSettings{autoAcceptTpa=" + autoAcceptTpa
                + ", formsDelivery=" + formsDelivery
                + ", acceptingRequests=" + acceptingRequests
                + ", ignored=" + ignored.size() + '}';
    }
}
