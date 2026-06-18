package cool.muyucloud.netherlink.link.service;

import cool.muyucloud.netherlink.link.model.LinkJoinOperation;
import cool.muyucloud.netherlink.link.model.LinkJoinTarget;

/** Owns outgoing join operations and their account-bound signaling connections. */
public interface LinkJoinService {
    /**
     * Starts or returns the existing operation for the same runtime and target Presence.
     * The runtime context must provide a client connection bridge.
     */
    LinkJoinOperation join(String runtimeKey, LinkJoinTarget target);

    /** Returns whether the runtime currently has an incomplete outgoing join. */
    boolean hasOutgoingJoin(String runtimeKey);

    /** Cancels joins and closes signaling resources owned by one runtime. */
    void shutdown(String runtimeKey);

    /** Cancels all joins and closes every client signaling resource. */
    void shutdown();
}
