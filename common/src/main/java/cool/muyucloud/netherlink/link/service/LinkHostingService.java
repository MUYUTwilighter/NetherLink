package cool.muyucloud.netherlink.link.service;

import cool.muyucloud.netherlink.link.model.LinkHostPublication;
import cool.muyucloud.netherlink.link.model.LinkPresenceUpdate;

import java.time.Duration;

/**
 * Owns host Presence, inbound signaling, and server-side WebRTC transport for all runtimes.
 * Each host key represents exactly one account-level runtime and must use an independent backend
 * identity and signaling connection even when several keys expose the same physical server.
 */
public interface LinkHostingService {
    /**
     * Starts or returns the publication for {@code hostKey}.
     *
     * <p>The runtime context must provide a server connection bridge and the runtime must already
     * be open. This call may wait up to {@code signalingReadyTimeout}; callers must not invoke it
     * on a Minecraft tick or render thread.</p>
     */
    LinkHostPublication publish(String hostKey, LinkPresenceUpdate presence, Duration signalingReadyTimeout);

    /** Idempotently closes the publication for one runtime key. */
    void close(String hostKey);

    /** Idempotently closes every publication and its signaling/transport resources. */
    void shutdown();
}
