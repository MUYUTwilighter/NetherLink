package cool.muyucloud.netherlink.link.official;

import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.account.MinecraftAccount;
import cool.muyucloud.netherlink.account.NetherLinkAuthException;
import cool.muyucloud.netherlink.link.exception.LinkFailures;
import cool.muyucloud.netherlink.link.exception.LinkUnauthorizedException;
import cool.muyucloud.netherlink.link.hook.LinkContextHooks;
import cool.muyucloud.netherlink.link.hook.LinkRuntimeContext;
import cool.muyucloud.netherlink.link.model.*;
import cool.muyucloud.netherlink.link.official.signaling.OfficialSignalingClient;
import cool.muyucloud.netherlink.link.service.LinkHostingService;
import cool.muyucloud.netherlink.link.transport.ServerP2PManager;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class OfficialHostingService implements LinkHostingService {
    private static final long PRESENCE_REFRESH_SECONDS = 10L;
    private static final ScheduledExecutorService MAINTENANCE = Executors.newScheduledThreadPool(2, r -> {
        Thread thread = new Thread(r, "NetherLink Official Presence");
        thread.setDaemon(true);
        return thread;
    });

    private final OfficialPresenceService presence;
    private final ConcurrentHashMap<String, Publication> publications = new ConcurrentHashMap<>();

    public OfficialHostingService(OfficialPresenceService presence) {
        this.presence = presence;
    }

    @Override
    public synchronized LinkHostPublication publish(String hostKey, LinkPresenceUpdate presenceUpdate, Duration signalingReadyTimeout) {
        Publication existing = this.publications.get(hostKey);
        if (existing != null) {
            return existing;
        }
        LinkRuntimeContext context = LinkContextHooks.require(hostKey);
        MinecraftAccount account = context.account();
        ServerP2PManager manager = new ServerP2PManager(
            hostKey,
            context.requireServerConnection(),
            new OfficialSignalingClient(account.getMcToken(), "NetherLink Signaling-" + hostKey)
        );
        manager.start();
        try {
            awaitSignalingReady(manager, signalingReadyTimeout);
            Publication publication = new Publication(this, hostKey, account, manager, this.presence, presenceUpdate);
            publication.start();
            this.publications.put(hostKey, publication);
            return publication;
        } catch (RuntimeException e) {
            manager.shutdown();
            throw e;
        }
    }

    @Override
    public void close(String hostKey) {
        Publication publication = this.publications.get(hostKey);
        if (publication != null) {
            publication.close();
        }
    }

    @Override
    public void shutdown() {
        this.publications.values().forEach(Publication::close);
    }

    private static void awaitSignalingReady(ServerP2PManager manager, Duration timeout) {
        try {
            manager.awaitSignalingReady(timeout).join();
        } catch (CompletionException e) {
            throw new NetherLinkAuthException("Signaling did not become ready before publishing presence", e);
        }
    }

    private static final class Publication implements LinkHostPublication {
        private final OfficialHostingService owner;
        private final String runtimeKey;
        private final MinecraftAccount account;
        private final ServerP2PManager manager;
        private final OfficialPresenceService presence;
        private final LinkPresenceUpdate presenceUpdate;
        private final AtomicBoolean closed = new AtomicBoolean();
        private volatile LinkPublicationSnapshot snapshot;
        private ScheduledFuture<?> maintenance;

        private Publication(OfficialHostingService owner, String runtimeKey, MinecraftAccount account, ServerP2PManager manager, OfficialPresenceService presence, LinkPresenceUpdate presenceUpdate) {
            this.owner = owner;
            this.runtimeKey = runtimeKey;
            this.account = account;
            this.manager = manager;
            this.presence = presence;
            this.presenceUpdate = presenceUpdate;
            this.snapshot = new LinkPublicationSnapshot(runtimeKey, LinkPublicationState.STARTING, null, null);
        }

        private void start() {
            this.refresh();
            this.maintenance = MAINTENANCE.scheduleWithFixedDelay(
                this::refreshSafely,
                PRESENCE_REFRESH_SECONDS,
                PRESENCE_REFRESH_SECONDS,
                TimeUnit.SECONDS
            );
        }

        private void refresh() {
            OfficialPresenceService.OfficialPresenceResult result;
            try {
                result = this.presence.publishHosting(this.account, this.presenceUpdate);
            } catch (LinkUnauthorizedException error) {
                if (!LinkContextHooks.require(this.runtimeKey).refreshCredentials()) {
                    throw error;
                }
                result = this.presence.publishHosting(this.account, this.presenceUpdate);
            }
            this.manager.updatePresence(result.profileIdsByPresence());
            this.snapshot = new LinkPublicationSnapshot(this.runtimeKey, LinkPublicationState.ACTIVE, result.presence(), null);
        }

        private void revokePresence() {
            try {
                this.presence.revoke(this.runtimeKey);
            } catch (LinkUnauthorizedException error) {
                if (!LinkContextHooks.require(this.runtimeKey).refreshCredentials()) {
                    throw error;
                }
                this.presence.revoke(this.runtimeKey);
            }
        }

        private synchronized void refreshSafely() {
            if (this.closed.get()) {
                return;
            }
            try {
                this.refresh();
            } catch (RuntimeException error) {
                LinkPresence current = this.snapshot.presence();
                this.snapshot = new LinkPublicationSnapshot(this.runtimeKey, LinkPublicationState.DEGRADED, current, LinkFailures.from(error));
                NliConstants.LOG.warn("Official Presence refresh failed for runtime {}", this.runtimeKey, error);
            }
        }

        @Override
        public LinkPublicationSnapshot snapshot() {
            return this.snapshot;
        }

        @Override
        public synchronized void close() {
            if (!this.closed.compareAndSet(false, true)) {
                return;
            }
            this.snapshot = new LinkPublicationSnapshot(this.runtimeKey, LinkPublicationState.CLOSED, this.snapshot.presence(), null);
            ScheduledFuture<?> task = this.maintenance;
            if (task != null) {
                task.cancel(false);
            }
            try {
                this.revokePresence();
            } finally {
                try {
                    this.manager.shutdown();
                } finally {
                    this.owner.publications.remove(this.runtimeKey, this);
                }
            }
        }
    }
}
