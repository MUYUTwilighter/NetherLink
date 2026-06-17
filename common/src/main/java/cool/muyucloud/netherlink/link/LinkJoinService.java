package cool.muyucloud.netherlink.link;

import java.util.concurrent.CompletableFuture;

public interface LinkJoinService {
    CompletableFuture<Void> join(LinkClientContext context, LinkFriendEntry target);

    boolean hasOutgoingJoin();

    void shutdown();
}
