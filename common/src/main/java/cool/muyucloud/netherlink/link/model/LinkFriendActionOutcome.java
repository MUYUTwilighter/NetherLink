package cool.muyucloud.netherlink.link.model;

import org.jetbrains.annotations.Nullable;

/**
 * Result of a friend graph mutation.
 * @param result broad result retained for presentation compatibility
 * @param relationship relationship after a successful mutation, when known
 * @param officialSync best-effort synchronization status with the official friend graph
 * @param failure normalized failure detail when {@code result} is not successful
 */
public record LinkFriendActionOutcome(
    LinkFriendActionResult result,
    @Nullable LinkFriendRelationship relationship,
    LinkOfficialSyncStatus officialSync,
    @Nullable LinkFailure failure
) {
}
