package cool.muyucloud.netherlink.link.hook;

import cool.muyucloud.netherlink.account.MinecraftAccount;
import cool.muyucloud.netherlink.link.bridge.LinkClientConnectionBridge;
import cool.muyucloud.netherlink.link.bridge.LinkServerConnectionBridge;
import org.jetbrains.annotations.Nullable;

/**
 * Game-owned context made available to a {@code LinkService} implementation.
 *
 * <p>This type deliberately contains no client-only Minecraft classes. Client
 * and dedicated-server code expose world integration through the two bridge
 * capabilities instead. The account supplies Minecraft identity material; backend-issued tokens
 * must never be stored in this context.</p>
 *
 * @param account Minecraft account bound to this runtime key
 * @param displayText human-readable process/server description, not protocol control data
 * @param clientConnection optional capability for handing an established channel to a client
 * @param serverConnection optional capability for accepting an established guest channel
 * @param credentialRefresh optional environment hook for synchronously refreshing Minecraft credentials
 */
public record LinkRuntimeContext(
    MinecraftAccount account,
    String displayText,
    @Nullable LinkClientConnectionBridge clientConnection,
    @Nullable LinkServerConnectionBridge serverConnection,
    @Nullable LinkCredentialRefresh credentialRefresh
) {
    public LinkRuntimeContext {
        if (displayText.isBlank()) {
            throw new IllegalArgumentException("Runtime display text must not be blank");
        }
    }

    /** Returns the client bridge or fails when this runtime cannot join another world. */
    public LinkClientConnectionBridge requireClientConnection() {
        if (this.clientConnection == null) {
            throw new IllegalStateException("Runtime context does not provide a client connection bridge");
        }
        return this.clientConnection;
    }

    /** Returns the server bridge or fails when this runtime cannot host guests. */
    public LinkServerConnectionBridge requireServerConnection() {
        if (this.serverConnection == null) {
            throw new IllegalStateException("Runtime context does not provide a server connection bridge");
        }
        return this.serverConnection;
    }

    /** Returns a copy with the client capability replaced and server capabilities preserved. */
    public LinkRuntimeContext withClientConnection(MinecraftAccount account, String displayText, LinkClientConnectionBridge connection) {
        return new LinkRuntimeContext(account, displayText, connection, this.serverConnection, this.credentialRefresh);
    }

    /** Returns a copy with server and refresh capabilities replaced and client capability preserved. */
    public LinkRuntimeContext withServerConnection(MinecraftAccount account, String displayText, LinkServerConnectionBridge connection, @Nullable LinkCredentialRefresh credentialRefresh) {
        return new LinkRuntimeContext(account, displayText, this.clientConnection, connection, credentialRefresh);
    }

    /** Returns a copy without the client capability. */
    public LinkRuntimeContext withoutClientConnection() {
        return new LinkRuntimeContext(this.account, this.displayText, null, this.serverConnection, this.credentialRefresh);
    }

    /** Returns a copy without server and credential-refresh capabilities. */
    public LinkRuntimeContext withoutServerConnection() {
        return new LinkRuntimeContext(this.account, this.displayText, this.clientConnection, null, null);
    }

    /** Returns whether at least one world integration bridge is present. */
    public boolean hasCapabilities() {
        return this.clientConnection != null || this.serverConnection != null;
    }

    /**
     * Runs the environment credential-refresh hook when available.
     * @return whether a refresh hook was present and invoked
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean refreshCredentials() {
        if (this.credentialRefresh == null) {
            return false;
        }
        this.credentialRefresh.refresh();
        return true;
    }
}
