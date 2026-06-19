package cool.muyucloud.netherlink.link.nli;

import cool.muyucloud.netherlink.account.NetherLinkAuthException;
import cool.muyucloud.netherlink.link.exception.LinkFailures;
import cool.muyucloud.netherlink.link.hook.LinkContextHooks;
import cool.muyucloud.netherlink.link.model.*;
import cool.muyucloud.netherlink.link.service.LinkHostingService;
import cool.muyucloud.netherlink.link.transport.ServerP2PManager;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

final class NliHostingService implements LinkHostingService {
    private final NliApiClient api;
    private final NliRuntimeService runtimes;
    private final ConcurrentHashMap<String, Publication> publications = new ConcurrentHashMap<>();

    NliHostingService(NliApiClient api, NliRuntimeService runtimes) {
        this.api = api;
        this.runtimes = runtimes;
    }

    @Override
    public synchronized LinkHostPublication publish(String hostKey, LinkPresenceUpdate update, Duration signalingReadyTimeout) {
        Publication existing = this.publications.get(hostKey);
        if (existing != null) return existing;
        NliSession session = this.runtimes.requireSession(hostKey);
        ServerP2PManager manager = new ServerP2PManager(
            hostKey,
            LinkContextHooks.require(hostKey).requireServerConnection(),
            new NliSignalingClient(this.api, session, "NetherLink NLI Host Signaling-" + hostKey)
        );
        manager.start();
        try {
            manager.awaitSignalingReady(signalingReadyTimeout).join();
            LinkPresence presence = this.runtimes.publishPresence(hostKey, update).join();
            manager.updatePresence(Map.of());
            Publication publication = new Publication(this, hostKey, manager, presence);
            this.publications.put(hostKey, publication);
            return publication;
        } catch (CompletionException error) {
            manager.shutdown();
            throw new NetherLinkAuthException("NLI hosting could not start", error);
        } catch (RuntimeException error) {
            manager.shutdown();
            throw error;
        }
    }

    @Override
    public void close(String hostKey) {
        Publication publication = this.publications.get(hostKey);
        if (publication != null) publication.close();
    }

    @Override
    public void shutdown() {
        this.publications.values().forEach(Publication::close);
    }

    private static final class Publication implements LinkHostPublication {
        private final NliHostingService owner;
        private final String runtimeKey;
        private final ServerP2PManager manager;
        private final AtomicBoolean closed = new AtomicBoolean();
        private volatile LinkPublicationSnapshot snapshot;

        private Publication(NliHostingService owner, String runtimeKey, ServerP2PManager manager, LinkPresence presence) {
            this.owner = owner;
            this.runtimeKey = runtimeKey;
            this.manager = manager;
            this.snapshot = new LinkPublicationSnapshot(runtimeKey, LinkPublicationState.ACTIVE, presence, null);
        }

        @Override
        public LinkPublicationSnapshot snapshot() {
            return this.snapshot;
        }

        @Override
        public void close() {
            if (!this.closed.compareAndSet(false, true)) return;
            LinkPresence presence = this.snapshot.presence();
            try {
                this.owner.runtimes.restoreOnline(this.runtimeKey).join();
                this.snapshot = new LinkPublicationSnapshot(this.runtimeKey, LinkPublicationState.CLOSED, presence, null);
            } catch (RuntimeException error) {
                this.snapshot = new LinkPublicationSnapshot(
                    this.runtimeKey, LinkPublicationState.DEGRADED, presence, LinkFailures.from(error)
                );
            } finally {
                this.manager.shutdown();
                this.owner.publications.remove(this.runtimeKey, this);
            }
        }
    }
}
