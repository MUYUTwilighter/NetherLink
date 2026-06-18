package cool.muyucloud.netherlink.link.service;

import cool.muyucloud.netherlink.link.model.LinkPresence;
import cool.muyucloud.netherlink.link.model.LinkPresenceUpdate;

/**
 * Backend-internal Presence publishing SPI.
 *
 * <p>Game-facing code does not call this service directly. Runtime and hosting implementations
 * own Presence creation, refresh cadence, and removal.</p>
 */
public interface LinkPresenceService {
    /** Publishes or refreshes Presence for exactly one account-level runtime. */
    @SuppressWarnings("unused")
    LinkPresence publish(String runtimeKey, LinkPresenceUpdate update);

    /** Idempotently removes Presence for exactly one account-level runtime. */
    void revoke(String runtimeKey);
}
