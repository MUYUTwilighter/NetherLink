package cool.muyucloud.netherlink.link;

import cool.muyucloud.netherlink.link.model.LinkBackendDescriptor;
import cool.muyucloud.netherlink.link.service.LinkFriendService;
import cool.muyucloud.netherlink.link.service.LinkHostingService;
import cool.muyucloud.netherlink.link.service.LinkJoinService;
import cool.muyucloud.netherlink.link.service.LinkRuntimeService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Complete backend implementation selected for the current process.
 *
 * <p>A service owns all backend I/O, private credentials, Presence maintenance, signaling
 * connections, and automatic runtime renewal. Game-facing code supplies only runtime context and
 * user intent. Implementations must support several independent runtime keys in one physical
 * process; a dedicated server may publish the same world through several accounts.</p>
 */
public interface LinkService {
    /** Returns stable metadata and optional capabilities for presentation and feature gating. */
    LinkBackendDescriptor descriptor();

    /** Returns the stable backend identifier. */
    default LinkBackendId id() {
        return this.descriptor().id();
    }

    /**
     * Creates a friend-service view authenticated as the account bound to {@code runtimeKey}.
     * The runtime must be open before an operation is executed.
     */
    LinkFriendService createFriendService(String runtimeKey);

    /** Returns the process-wide runtime manager for this backend. */
    LinkRuntimeService runtime();

    /** Returns the process-wide hosting manager for this backend. */
    LinkHostingService hosting();

    /** Returns the process-wide outgoing join manager for this backend. */
    LinkJoinService joining();

    /**
     * Stops new work and closes joins, publications, signaling connections, and runtimes.
     * The returned future completes only after asynchronous backend cleanup has completed.
     */
    default CompletableFuture<Void> shutdown() {
        RuntimeException failure = null;
        try {
            this.joining().shutdown();
        } catch (RuntimeException error) {
            failure = error;
        }
        try {
            this.hosting().shutdown();
        } catch (RuntimeException error) {
            if (failure == null) {
                failure = error;
            } else {
                failure.addSuppressed(error);
            }
        }

        CompletableFuture<Void> close;
        try {
            close = this.runtime().closeAll();
        } catch (RuntimeException error) {
            if (failure == null) {
                return CompletableFuture.failedFuture(error);
            }
            failure.addSuppressed(error);
            return CompletableFuture.failedFuture(failure);
        }
        if (failure == null) {
            return close;
        }
        RuntimeException shutdownFailure = failure;
        return close.handle((_, closeError) -> {
            if (closeError != null) {
                shutdownFailure.addSuppressed(closeError);
            }
            throw new CompletionException(shutdownFailure);
        });
    }
}
