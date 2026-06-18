package cool.muyucloud.netherlink.link.model;

/**
 * Idempotently closeable handle for one account runtime publishing one local server.
 * Implementations own Presence refresh and inbound signaling for the handle's lifetime.
 */
public interface LinkHostPublication extends AutoCloseable {
    /** Returns the latest token-free state snapshot without performing network I/O. */
    LinkPublicationSnapshot snapshot();

    /** Stops refresh/signaling and revokes Presence; repeated calls have no effect. */
    @Override
    void close();
}
