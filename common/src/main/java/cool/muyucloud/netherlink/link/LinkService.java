package cool.muyucloud.netherlink.link;

import cool.muyucloud.netherlink.link.model.LinkTerms;
import cool.muyucloud.netherlink.link.model.LinkTermsState;
import cool.muyucloud.netherlink.link.service.LinkFriendService;
import cool.muyucloud.netherlink.link.service.LinkHostingService;
import cool.muyucloud.netherlink.link.service.LinkJoinService;
import cool.muyucloud.netherlink.link.service.LinkRuntimeService;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
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
    /** Optional backend features used for presentation and feature gating. */
    enum Capability {
        FRIENDS,
        FRIEND_SETTINGS,
        HOSTING,
        JOINING,
        SELF_PRESENCE,
        MULTI_ACCOUNT_RUNTIME,
        MULTI_PRESENCE
    }

    /** Returns the stable protocol/backend identifier. */
    ResourceLocation id();

    /**
     * Returns the localized backend name. By default, {@code namespace:path} resolves to the
     * translation key {@code namespace.link.path}; implementations may override this for a
     * custom component.
     */
    default Component name() {
        ResourceLocation id = this.id();
        return Component.translatable(id.getNamespace() + ".link." + id.getPath());
    }

    /**
     * Returns the client-side settings renderer identifier for this backend. This deliberately
     * stays as data so the common service abstraction never references client-only classes.
     */
    default ResourceLocation settingsRendererId() {
        return this.id();
    }

    /**
     * Fetches terms that must be accepted before this backend is first used. Backends without
     * service-specific terms return an empty optional.
     */
    default CompletableFuture<Optional<LinkTerms>> terms(String language) {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    /**
     * Runtime cache scope for terms checks. Services should include backend-specific
     * identity here when two instances with the same {@link #id()} may expose different terms.
     */
    default String termsCacheScope() {
        return this.id().toString();
    }

    /**
     * Checks the backend's current terms against the backend-owned acceptance store.
     * Implementations with persistent terms should override this together with
     * {@link #acceptTerms(LinkTerms)}.
     */
    default CompletableFuture<LinkTermsState> termsStatus(String language) {
        return this.terms(language).thenApply(terms -> terms
            .map(LinkTermsState::unaccepted)
            .orElseGet(LinkTermsState::unavailable));
    }

    /** Records acceptance of the supplied terms in the backend-owned acceptance store. */
    default CompletableFuture<Void> acceptTerms(LinkTerms terms) {
        return CompletableFuture.completedFuture(null);
    }

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
        return close.handle((ignored1, closeError) -> {
            if (closeError != null) {
                shutdownFailure.addSuppressed(closeError);
            }
            throw new CompletionException(shutdownFailure);
        });
    }
}
