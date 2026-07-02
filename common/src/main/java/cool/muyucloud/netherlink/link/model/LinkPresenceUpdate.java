package cool.muyucloud.netherlink.link.model;

import org.jetbrains.annotations.Nullable;

import java.time.Duration;

/**
 * Desired public Presence state supplied to a hosting implementation.
 * TTL is advisory and may be clamped by the backend; the implementation owns refresh scheduling.
 */
public record LinkPresenceUpdate(
    LinkPresenceStatus status,
    boolean joinable,
    String displayText,
    @Nullable String sessionId,
    @Nullable String endpoint,
    Duration ttl
) {
    /** Creates the conventional joinable hosting update with a 90-second requested TTL. */
    public static LinkPresenceUpdate hosting(String displayText) {
        return new LinkPresenceUpdate(LinkPresenceStatus.HOSTING, true, displayText, null, null, Duration.ofSeconds(90L));
    }
}
