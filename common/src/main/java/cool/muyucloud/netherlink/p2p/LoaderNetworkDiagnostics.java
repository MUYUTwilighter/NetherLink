package cool.muyucloud.netherlink.p2p;

import cool.muyucloud.netherlink.NliConstants;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

public final class LoaderNetworkDiagnostics {
    private static final String FORGE_DIAGNOSTICS = "cool.muyucloud.netherlink.platform.ForgeRtcDiagnostics";
    private static final @Nullable Method LOG_CLIENT_CONNECTION = find(
        "logClientConnection",
        Connection.class,
        String.class
    );
    private static final @Nullable Method LOG_CLIENT_INTENTION = find(
        "logClientIntention",
        ClientIntentionPacket.class,
        String.class
    );
    private static final @Nullable Method LOG_SERVER_INTENTION = find(
        "logServerIntention",
        Connection.class,
        ClientIntentionPacket.class,
        String.class,
        UUID.class
    );

    private LoaderNetworkDiagnostics() {
    }

    public static void logClientConnection(Connection connection, String phase) {
        invoke(LOG_CLIENT_CONNECTION, connection, phase);
    }

    public static void logClientIntention(ClientIntentionPacket packet, String phase) {
        invoke(LOG_CLIENT_INTENTION, packet, phase);
    }

    public static void logServerIntention(Connection connection, ClientIntentionPacket packet, String phase, @Nullable UUID profileId) {
        invoke(LOG_SERVER_INTENTION, connection, packet, phase, profileId);
    }

    private static @Nullable Method find(String name, Class<?>... parameterTypes) {
        try {
            Class<?> diagnostics = Class.forName(FORGE_DIAGNOSTICS);
            return diagnostics.getMethod(name, parameterTypes);
        } catch (ClassNotFoundException e) {
            return null;
        } catch (NoSuchMethodException e) {
            NliConstants.LOG.warn("[P2P-Netty] Forge RTC diagnostics method {} is missing", name, e);
            return null;
        }
    }

    private static void invoke(@Nullable Method method, Object... args) {
        if (method == null) {
            return;
        }
        try {
            method.invoke(null, args);
        } catch (IllegalAccessException e) {
            NliConstants.LOG.warn("[P2P-Netty] Failed to access loader RTC diagnostics", e);
        } catch (InvocationTargetException e) {
            NliConstants.LOG.warn("[P2P-Netty] Loader RTC diagnostics failed", e.getCause());
        }
    }
}
