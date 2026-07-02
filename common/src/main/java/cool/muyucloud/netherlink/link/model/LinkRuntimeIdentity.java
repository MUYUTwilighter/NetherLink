package cool.muyucloud.netherlink.link.model;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Public identity issued or resolved for an open account runtime.
 * Backend instance tokens are intentionally excluded.
 */
public record LinkRuntimeIdentity(
    UUID profileId,
    String name,
    @Nullable String presenceId,
    @Nullable Instant expiresAt
) {
}
