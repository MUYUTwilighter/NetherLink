package cool.muyucloud.netherlink.link;

import cool.muyucloud.netherlink.link.service.LinkFriendService;
import cool.muyucloud.netherlink.link.service.LinkHostingService;
import cool.muyucloud.netherlink.link.service.LinkJoinService;
import cool.muyucloud.netherlink.link.service.LinkRuntimeService;
import net.minecraft.network.chat.Component;

import java.util.Set;
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
    /** Stable identifiers for selectable Link protocol/backend versions. */
    enum Id {
        MOJ_26_2_S8(Component.translatable("netherlink.backend.moj_26_2_s8")),
        NLI_V1(Component.translatable("netherlink.backend.nli_v1"));

        private final Component component;

        Id(Component component) {
            this.component = component;
        }

        /** Returns the localized backend label. */
        public Component component() {
            return this.component;
        }
    }

    /** Optional backend features used for presentation and feature gating. */
    enum Capability {
        FRIENDS,
        HOSTING,
        JOINING,
        MULTI_ACCOUNT_RUNTIME,
        MULTI_PRESENCE
    }

    /** Returns the stable protocol/backend identifier. */
    Id id();

    /** Returns the immutable set of optional features supported by this backend. */
    Set<Capability> capabilities();

    /** Returns whether this backend supports an optional feature. */
    @SuppressWarnings("unused")
    default boolean supports(Capability capability) {
        return this.capabilities().contains(capability);
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
