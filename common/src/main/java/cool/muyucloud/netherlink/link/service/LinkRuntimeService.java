package cool.muyucloud.netherlink.link.service;

import cool.muyucloud.netherlink.link.model.LinkRuntimeIdentity;
import cool.muyucloud.netherlink.link.model.LinkRuntimeSnapshot;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Manages account-level backend authorization runtimes.
 *
 * <p>One physical process may own several runtimes. Each key binds exactly one Minecraft account
 * to one private backend credential and one public Presence identity. Implementations may share
 * connection pools, but must never multiplex account credentials or signaling source identities.</p>
 */
public interface LinkRuntimeService {
    /** Conventional key for the launcher account in a physical client process. */
    String CLIENT_KEY = "client";

    /**
     * Opens the keyed runtime, or returns its current identity when it is already open.
     * Implementations own credential renewal and keep the runtime valid until {@link #close(String)} is called.
     */
    CompletableFuture<LinkRuntimeIdentity> open(String key);

    /**
     * Opens the keyed runtime and publishes a non-joinable online presence when the backend
     * supports account-level online presence. Backends that only publish joinable hosting
     * presences may treat this as a plain {@link #open(String)}.
     */
    default CompletableFuture<LinkRuntimeIdentity> publishOnline(String key) {
        return this.open(key);
    }

    /**
     * Renews implementation-private credentials for the keyed runtime while preserving its public Presence identity.
     */
    @SuppressWarnings("unused")
    CompletableFuture<LinkRuntimeIdentity> renew(String key);

    /**
     * Closes the runtime and invalidates its Presence, private credentials, and signaling state.
     * The operation is idempotent from the caller's perspective.
     */
    CompletableFuture<Void> close(String key);

    /** Closes every runtime owned by this backend instance. */
    CompletableFuture<Void> closeAll();

    /** Returns the last active public identity, or {@code null} if the runtime is not open. */
    @Nullable LinkRuntimeIdentity current(String key);

    /** Returns a token-free state snapshot suitable for UI or command output. */
    LinkRuntimeSnapshot snapshot(String key);
}
