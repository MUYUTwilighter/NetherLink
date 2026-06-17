package cool.muyucloud.netherlink.link.service;

import cool.muyucloud.netherlink.link.model.LinkFriendEntry;

import java.util.concurrent.CompletableFuture;

public interface LinkJoinService {
    CompletableFuture<Void> join(LinkFriendEntry target);

    boolean hasOutgoingJoin();

    void shutdown();
}
