package cool.muyucloud.netherlink.link.model;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * Public, backend-neutral activity record for one account runtime.
 *
 * <p>{@code presenceId} is public routing data, not an authentication token. It may be absent for
 * a backend that authenticates the local publisher without revealing its own routing id. Friend
 * Presence entries intended for joining must provide it. Session and endpoint values are optional
 * backend-neutral metadata and must not contain private credentials.</p>
 */
public record LinkPresence(
    @Nullable String presenceId,
    LinkPresenceStatus status,
    boolean joinable,
    String displayText,
    @Nullable String sessionId,
    @Nullable String endpoint,
    @Nullable Instant updatedAt,
    @Nullable Instant expiresAt
) {
}
