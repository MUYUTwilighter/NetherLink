package cool.muyucloud.netherlink.link.model;

import java.util.concurrent.CompletableFuture;

/** Observable and cancellable outgoing join attempt for one runtime and target Presence. */
public interface LinkJoinOperation {
    /** Returns the latest immutable state snapshot without blocking. */
    LinkJoinSnapshot snapshot();

    /** Completes after channel hand-off succeeds or exceptionally on rejection, failure, or cancellation. */
    CompletableFuture<Void> completion();

    /**
     * Requests cancellation and aborts active transport negotiation.
     * @return {@code true} if this call changed an incomplete operation to cancelled
     */
    @SuppressWarnings("unused")
    boolean cancel();
}
