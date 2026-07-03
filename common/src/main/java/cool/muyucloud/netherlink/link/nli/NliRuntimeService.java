package cool.muyucloud.netherlink.link.nli;

import com.google.gson.JsonObject;
import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.account.MinecraftAccount;
import cool.muyucloud.netherlink.link.exception.LinkFailures;
import cool.muyucloud.netherlink.link.hook.LinkContextHooks;
import cool.muyucloud.netherlink.link.hook.LinkRuntimeContext;
import cool.muyucloud.netherlink.link.model.*;
import cool.muyucloud.netherlink.link.service.LinkRuntimeService;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.*;

final class NliRuntimeService implements LinkRuntimeService {
    private static final Duration RENEW_MARGIN = Duration.ofMinutes(5L);
    private static final Duration RENEW_RETRY = Duration.ofSeconds(30L);
    private static final Duration PRESENCE_REFRESH = Duration.ofSeconds(45L);
    private static final Duration ONLINE_TTL = Duration.ofSeconds(90L);

    private final NliApiClient api;
    private final ScheduledExecutorService maintenance;
    private final ConcurrentHashMap<String, CompletableFuture<NliSession>> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LinkFailure> failures = new ConcurrentHashMap<>();

    NliRuntimeService(NliApiClient api) {
        this.api = api;
        this.maintenance = Executors.newScheduledThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "NetherLink NLI Runtime");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public CompletableFuture<LinkRuntimeIdentity> open(String key) {
        return this.session(key).thenApply(NliSession::identity);
    }

    @Override
    public CompletableFuture<LinkRuntimeIdentity> publishOnline(String key) {
        return this.restoreOnline(key).thenApply(ignored -> this.requireSession(key).identity());
    }

    CompletableFuture<NliSession> session(String key) {
        CompletableFuture<NliSession> future = this.sessions.computeIfAbsent(key, this::register);
        return future.whenComplete((ignored1, error) -> {
            if (error != null) this.sessions.remove(key, future);
        });
    }

    NliSession requireSession(String key) {
        CompletableFuture<NliSession> future = this.sessions.get(key);
        if (future == null || !future.isDone() || future.isCompletedExceptionally()) {
            throw new IllegalStateException("NLI runtime is not open: " + key);
        }
        return future.join();
    }

    private CompletableFuture<NliSession> register(String key) {
        LinkRuntimeContext context = LinkContextHooks.require(key);
        MinecraftAccount account = context.account();
        String minecraftToken = account.getMcToken();
        if (minecraftToken == null || minecraftToken.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Minecraft access token is missing"));
        }
        String instanceName = context.displayText();
        JsonObject body = new JsonObject();
        body.addProperty("displayText", instanceName);
        return this.api.post("v1/instances", minecraftToken, body).thenApply(json -> {
            NliSession session = new NliSession(
                key,
                UUID.fromString(NliApiClient.requiredString(json, "profileId")),
                NliApiClient.requiredString(json, "name"),
                NliApiClient.requiredString(json, "presenceId"),
                NliApiClient.requiredString(json, "instanceToken"),
                Instant.parse(NliApiClient.requiredString(json, "expiresAt"))
            );
            session.desiredPresence(online(instanceName));
            this.scheduleRenewal(session);
            this.schedulePresence(session);
            this.failures.remove(key);
            return session;
        });
    }

    @Override
    public CompletableFuture<LinkRuntimeIdentity> renew(String key) {
        return this.session(key).thenCompose(this::renewSession).thenApply(NliSession::identity);
    }

    private CompletableFuture<NliSession> renewSession(NliSession session) {
        return this.api.post("v1/instances/renew", session.token(), null).thenApply(json -> {
            session.rotate(
                NliApiClient.requiredString(json, "instanceToken"),
                Instant.parse(NliApiClient.requiredString(json, "expiresAt"))
            );
            this.failures.remove(session.runtimeKey());
            this.scheduleRenewal(session);
            return session;
        });
    }

    private synchronized void scheduleRenewal(NliSession session) {
        ScheduledFuture<?> old = session.renewalTask();
        if (old != null) old.cancel(false);
        long remaining = Math.max(1L, Duration.between(Instant.now(), session.expiresAt()).toMillis());
        long delay = Math.max(1_000L, remaining - Math.min(RENEW_MARGIN.toMillis(), remaining / 3L));
        session.renewalTask(this.maintenance.schedule(() -> this.renewSession(session).whenComplete((ignored2, error) -> {
            if (error != null && this.isCurrent(session)) {
                this.failures.put(session.runtimeKey(), LinkFailures.from(error));
                NliConstants.LOG.warn("NLI runtime renewal failed for {}", session.runtimeKey(), error);
                session.renewalTask(this.maintenance.schedule(
                    () -> this.renewSession(session).whenComplete((ignored3, retryError) -> {
                        if (retryError != null && this.isCurrent(session)) this.scheduleRetry(session);
                    }),
                    RENEW_RETRY.toMillis(), TimeUnit.MILLISECONDS
                ));
            }
        }), delay, TimeUnit.MILLISECONDS));
    }

    private void scheduleRetry(NliSession session) {
        this.failures.put(session.runtimeKey(), new LinkFailure(
            LinkFailureCode.SERVICE_UNAVAILABLE, "NLI runtime renewal is retrying", true
        ));
        session.renewalTask(this.maintenance.schedule(
            () -> this.renewSession(session).whenComplete((ignored4, error) -> {
                if (error != null && this.isCurrent(session)) this.scheduleRetry(session);
            }),
            RENEW_RETRY.toMillis(), TimeUnit.MILLISECONDS
        ));
    }

    private void schedulePresence(NliSession session) {
        session.presenceTask(this.maintenance.scheduleWithFixedDelay(
            () -> this.refreshPresence(session),
            PRESENCE_REFRESH.toSeconds(),
            PRESENCE_REFRESH.toSeconds(),
            TimeUnit.SECONDS
        ));
    }

    private void refreshPresence(NliSession session) {
        LinkPresenceUpdate desired = session.desiredPresence();
        if (desired == null || !this.isCurrent(session)) return;
        this.publishPresence(session.runtimeKey(), desired).exceptionally(error -> {
            this.failures.put(session.runtimeKey(), LinkFailures.from(error));
            NliConstants.LOG.warn("NLI Presence refresh failed for {}", session.runtimeKey(), error);
            return null;
        });
    }

    CompletableFuture<LinkPresence> publishPresence(String key, LinkPresenceUpdate update) {
        return this.session(key).thenCompose(session -> {
            session.desiredPresence(update);
            JsonObject body = new JsonObject();
            body.addProperty("status", wireStatus(update.status()));
            body.addProperty("joinable", update.joinable());
            body.addProperty("displayText", update.displayText());
            body.addProperty("ttlSeconds", update.ttl().toSeconds());
            if (update.sessionId() != null) body.addProperty("sessionId", update.sessionId());
            if (update.endpoint() != null) body.addProperty("endpoint", update.endpoint());
            return this.api.put("v1/presence", session.token(), body).thenApply(json -> {
                Instant expiresAt = Instant.parse(NliApiClient.requiredString(json, "expiresAt"));
                this.failures.remove(key);
                return new LinkPresence(
                    session.presenceId(), update.status(), update.joinable(), update.displayText(),
                    update.sessionId(), update.endpoint(), Instant.now(), expiresAt
                );
            });
        });
    }

    CompletableFuture<LinkPresence> restoreOnline(String key) {
        LinkRuntimeContext context = LinkContextHooks.require(key);
        return this.publishPresence(key, online(context.displayText()));
    }

    @Override
    public CompletableFuture<Void> close(String key) {
        CompletableFuture<NliSession> future = this.sessions.remove(key);
        this.failures.remove(key);
        if (future == null) return CompletableFuture.completedFuture(null);
        return future.handle((session, error) -> session).thenCompose(session -> {
            if (session == null) return CompletableFuture.completedFuture(null);
            session.cancelTasks();
            return this.api.delete("v1/instances/current", session.token(), null)
                .exceptionally(error -> {
                    if (LinkFailures.from(error).code() != LinkFailureCode.UNAUTHORIZED) throw new CompletionException(error);
                    return null;
                });
        });
    }

    @Override
    public CompletableFuture<Void> closeAll() {
        return CompletableFuture.allOf(new ArrayList<>(this.sessions.keySet()).stream()
            .map(this::close).toArray(CompletableFuture[]::new)).whenComplete((ignored5, ignored101) -> this.maintenance.shutdown());
    }

    @Override
    public @Nullable LinkRuntimeIdentity current(String key) {
        CompletableFuture<NliSession> future = this.sessions.get(key);
        if (future == null || !future.isDone() || future.isCompletedExceptionally()) return null;
        return future.join().identity();
    }

    @Override
    public LinkRuntimeSnapshot snapshot(String key) {
        LinkRuntimeIdentity identity = this.current(key);
        LinkFailure failure = this.failures.get(key);
        LinkRuntimeState state = identity == null ? LinkRuntimeState.CLOSED
            : failure == null ? LinkRuntimeState.ACTIVE : LinkRuntimeState.DEGRADED;
        return new LinkRuntimeSnapshot(key, state, identity, failure);
    }

    private boolean isCurrent(NliSession session) {
        CompletableFuture<NliSession> current = this.sessions.get(session.runtimeKey());
        return current != null && current.getNow(null) == session;
    }

    private static String wireStatus(LinkPresenceStatus status) {
        return switch (status) {
            case HOSTING -> "HOSTING";
            case ONLINE -> "ONLINE";
            case PLAYING_OFFLINE, PLAYING_REALMS, PLAYING_SERVER -> "IN_GAME";
            case OFFLINE, UNKNOWN -> throw new IllegalArgumentException("NLI Presence cannot publish " + status);
        };
    }

    private static LinkPresenceUpdate online(String displayText) {
        return new LinkPresenceUpdate(LinkPresenceStatus.ONLINE, false, displayText, null, null, ONLINE_TTL);
    }
}
