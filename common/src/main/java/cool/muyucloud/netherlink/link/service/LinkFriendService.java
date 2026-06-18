package cool.muyucloud.netherlink.link.service;

import cool.muyucloud.netherlink.link.model.LinkFriendActionOutcome;
import cool.muyucloud.netherlink.link.model.LinkFriendSnapshot;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Friend graph operations bound to one account runtime.
 *
 * <p>Implementations perform network work asynchronously. Completion callbacks are not guaranteed
 * to run on a Minecraft thread. A service instance must not be reused for another runtime key.</p>
 */
public interface LinkFriendService {
    /** Fetches a complete backend-neutral friend and Presence snapshot. */
    CompletableFuture<LinkFriendSnapshot> refresh();

    /** Sends a friend request to the currently resolved Minecraft profile name. */
    CompletableFuture<LinkFriendActionOutcome> add(String name);

    /** Removes an established friendship. */
    CompletableFuture<LinkFriendActionOutcome> remove(UUID profileId);

    /** Accepts an incoming request from {@code profileId}. */
    CompletableFuture<LinkFriendActionOutcome> accept(UUID profileId);

    /** Declines an incoming request from {@code profileId}. */
    CompletableFuture<LinkFriendActionOutcome> decline(UUID profileId);

    /** Revokes an outgoing request to {@code profileId}. */
    CompletableFuture<LinkFriendActionOutcome> revoke(UUID profileId);
}
