package cool.muyucloud.netherlink.link.hook;

import cool.muyucloud.netherlink.account.MinecraftAccount;
import cool.muyucloud.netherlink.link.bridge.LinkClientConnectionBridge;
import cool.muyucloud.netherlink.link.bridge.LinkServerConnectionBridge;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, service-neutral runtime context registry populated by game-facing code.
 *
 * <p>A runtime key is an opaque process-local identifier, not a player name or backend token. It
 * must remain stable while its runtime is open. Dedicated servers should derive keys from stable
 * profile UUIDs so account renames do not orphan backend state.</p>
 *
 * <p>Client and server capabilities may coexist under one key for an integrated server. Removing
 * one capability preserves the other. This class and all referenced types are dedicated-server
 * safe and must remain free of {@code net.minecraft.client} references.</p>
 */
public final class LinkContextHooks {
    private static final Map<String, LinkRuntimeContext> CONTEXTS = new ConcurrentHashMap<>();

    private LinkContextHooks() {
    }

    /**
     * Replaces the complete context and returns a conditional removal handle.
     * Closing an old handle never removes a newer replacement context.
     */
    public static Registration register(String runtimeKey, LinkRuntimeContext context) {
        if (runtimeKey.isBlank()) {
            throw new IllegalArgumentException("Runtime key must not be blank");
        }
        CONTEXTS.put(runtimeKey, context);
        return new Registration(runtimeKey, context);
    }

    /** Adds or replaces only the client/world-join capability for a runtime. */
    public static void setClientConnection(String runtimeKey, MinecraftAccount account, String displayText, LinkClientConnectionBridge connection) {
        requireKey(runtimeKey);
        CONTEXTS.compute(runtimeKey, (ignored1, existing) -> existing == null
            ? new LinkRuntimeContext(account, displayText, connection, null, null)
            : existing.withClientConnection(account, displayText, connection));
    }

    /** Adds or replaces only the server/guest-accept capability for a runtime. */
    public static void setServerConnection(String runtimeKey, MinecraftAccount account, String displayText, LinkServerConnectionBridge connection) {
        setServerConnection(runtimeKey, account, displayText, connection, null);
    }

    /**
     * Adds or replaces the server capability and optional synchronous Minecraft credential refresh
     * hook. The refresh hook is environment support, not backend authentication state.
     */
    public static void setServerConnection(String runtimeKey, MinecraftAccount account, String displayText, LinkServerConnectionBridge connection, @Nullable LinkCredentialRefresh credentialRefresh) {
        requireKey(runtimeKey);
        CONTEXTS.compute(runtimeKey, (ignored1, existing) -> existing == null
            ? new LinkRuntimeContext(account, displayText, null, connection, credentialRefresh)
            : existing.withServerConnection(account, displayText, connection, credentialRefresh));
    }

    /** Removes the client capability, deleting the context only when no capability remains. */
    @SuppressWarnings("unused")
    public static void removeClientConnection(String runtimeKey) {
        CONTEXTS.computeIfPresent(runtimeKey, (ignored1, existing) -> {
            LinkRuntimeContext next = existing.withoutClientConnection();
            return next.hasCapabilities() ? next : null;
        });
    }

    /** Removes the server and credential-refresh capabilities, preserving any client capability. */
    public static void removeServerConnection(String runtimeKey) {
        CONTEXTS.computeIfPresent(runtimeKey, (ignored1, existing) -> {
            LinkRuntimeContext next = existing.withoutServerConnection();
            return next.hasCapabilities() ? next : null;
        });
    }

    /** Returns the current immutable context, or {@code null} when the key is unregistered. */
    public static @Nullable LinkRuntimeContext get(String runtimeKey) {
        return CONTEXTS.get(runtimeKey);
    }

    /**
     * Returns the current context.
     * @throws IllegalStateException when the key is unregistered
     */
    public static LinkRuntimeContext require(String runtimeKey) {
        LinkRuntimeContext context = CONTEXTS.get(runtimeKey);
        if (context == null) {
            throw new IllegalStateException("Link runtime context is not registered: " + runtimeKey);
        }
        return context;
    }

    /** Unconditionally removes all capabilities registered under a runtime key. */
    public static void remove(String runtimeKey) {
        CONTEXTS.remove(runtimeKey);
    }

    private static void requireKey(String runtimeKey) {
        if (runtimeKey.isBlank()) {
            throw new IllegalArgumentException("Runtime key must not be blank");
        }
    }

    /** Idempotent-in-effect handle that conditionally removes the exact registered context. */
    public static final class Registration implements AutoCloseable {
        private final String runtimeKey;
        private final LinkRuntimeContext context;

        private Registration(String runtimeKey, LinkRuntimeContext context) {
            this.runtimeKey = runtimeKey;
            this.context = context;
        }

        /** Removes the context only if it has not since been replaced. */
        @Override
        public void close() {
            CONTEXTS.remove(this.runtimeKey, this.context);
        }
    }
}
