package cool.muyucloud.netherlink.link.nli;

import cool.muyucloud.netherlink.link.model.LinkPresenceUpdate;
import cool.muyucloud.netherlink.link.model.LinkRuntimeIdentity;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;

final class NliSession {
    private final String runtimeKey;
    private final UUID profileId;
    private final String name;
    private final String presenceId;
    private final List<Runnable> tokenListeners = new CopyOnWriteArrayList<>();
    private volatile String token;
    private volatile Instant expiresAt;
    private volatile @Nullable LinkPresenceUpdate desiredPresence;
    private volatile @Nullable ScheduledFuture<?> renewalTask;
    private volatile @Nullable ScheduledFuture<?> presenceTask;

    NliSession(String runtimeKey, UUID profileId, String name, String presenceId, String token, Instant expiresAt) {
        this.runtimeKey = runtimeKey;
        this.profileId = profileId;
        this.name = name;
        this.presenceId = presenceId;
        this.token = token;
        this.expiresAt = expiresAt;
    }

    String runtimeKey() { return this.runtimeKey; }
    String presenceId() { return this.presenceId; }
    String token() { return this.token; }
    Instant expiresAt() { return this.expiresAt; }
    @Nullable LinkPresenceUpdate desiredPresence() { return this.desiredPresence; }
    void desiredPresence(@Nullable LinkPresenceUpdate update) { this.desiredPresence = update; }
    @Nullable ScheduledFuture<?> renewalTask() { return this.renewalTask; }
    void renewalTask(@Nullable ScheduledFuture<?> task) { this.renewalTask = task; }
    void presenceTask(@Nullable ScheduledFuture<?> task) { this.presenceTask = task; }

    synchronized void rotate(String token, Instant expiresAt) {
        this.token = token;
        this.expiresAt = expiresAt;
        this.tokenListeners.forEach(Runnable::run);
    }

    void addTokenListener(Runnable listener) { this.tokenListeners.add(listener); }
    void removeTokenListener(Runnable listener) { this.tokenListeners.remove(listener); }

    LinkRuntimeIdentity identity() {
        return new LinkRuntimeIdentity(this.profileId, this.name, this.presenceId, this.expiresAt);
    }

    void cancelTasks() {
        ScheduledFuture<?> renewal = this.renewalTask;
        if (renewal != null) renewal.cancel(false);
        ScheduledFuture<?> presence = this.presenceTask;
        if (presence != null) presence.cancel(false);
        this.renewalTask = null;
        this.presenceTask = null;
    }
}
