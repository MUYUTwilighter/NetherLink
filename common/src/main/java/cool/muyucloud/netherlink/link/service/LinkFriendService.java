package cool.muyucloud.netherlink.link.service;

import cool.muyucloud.netherlink.link.model.LinkFriendActionResult;
import cool.muyucloud.netherlink.link.model.LinkFriendSnapshot;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface LinkFriendService {
    CompletableFuture<LinkFriendSnapshot> refresh();

    CompletableFuture<LinkFriendActionResult> add(String name);

    CompletableFuture<LinkFriendActionResult> remove(UUID profileId);

    CompletableFuture<LinkFriendActionResult> accept(UUID profileId);

    CompletableFuture<LinkFriendActionResult> decline(UUID profileId);

    CompletableFuture<LinkFriendActionResult> revoke(UUID profileId);
}
