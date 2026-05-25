package cool.muyucloud.netherlink.platform;

import cool.muyucloud.netherlink.NliConstants;
import io.netty.util.AttributeKey;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraftforge.network.ConnectionType;
import net.minecraftforge.network.NetworkConstants;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.function.Supplier;

@SuppressWarnings("unused")
public final class ForgeRtcDiagnostics {
    private static final @Nullable AttributeKey<String> FML_NETVERSION = attribute("FML_NETVERSION");
    private static final @Nullable AttributeKey<?> FML_HANDSHAKE_HANDLER = attribute("FML_HANDSHAKE_HANDLER");

    private ForgeRtcDiagnostics() {
    }

    public static void logClientConnection(Connection connection, String phase) {
        NliConstants.LOG.info(
            "[P2P-Netty][forge][client] phase={} fmlVersion={} connectionType={} handshakePresent={} pipeline={}",
            phase,
            fmlVersion(connection),
            connectionType(connection),
            handshakePresent(connection),
            connection.channel().pipeline().names()
        );
    }

    public static void logClientIntention(ClientIntentionPacket packet, String phase) {
        logIntention("[P2P-Netty][forge][client]", packet, phase, null, null);
    }

    public static void logServerIntention(Connection connection, ClientIntentionPacket packet, String phase, @Nullable UUID profileId) {
        logIntention("[P2P-Netty][forge][server]", packet, phase, connection, profileId);
    }

    private static void logIntention(String prefix, ClientIntentionPacket packet, String phase, @Nullable Connection connection, @Nullable UUID profileId) {
        String fmlVersion = packet.getFMLVersion();
        ConnectionType type = ConnectionType.forVersionFlag(fmlVersion);
        NliConstants.LOG.info(
            "{} phase={} profile={} host={} port={} intention={} packetFmlVersion={} inferredType={} channelFmlVersion={} channelType={} handshakePresent={} pipeline={}",
            prefix,
            phase,
            profileId,
            packet.getHostName(),
            packet.getPort(),
            packet.getIntention(),
            fmlVersion,
            type,
            connection == null ? "<none>" : fmlVersion(connection),
            connection == null ? "<none>" : connectionType(connection),
            connection != null && handshakePresent(connection),
            connection == null ? "<none>" : connection.channel().pipeline().names()
        );
    }

    private static String fmlVersion(Connection connection) {
        if (FML_NETVERSION == null) {
            return "<attr-missing>";
        }
        return String.valueOf(connection.channel().attr(FML_NETVERSION).get());
    }

    private static String connectionType(Connection connection) {
        if (FML_NETVERSION == null || connection.channel().attr(FML_NETVERSION).get() == null) {
            return "<unset>";
        }
        try {
            Supplier<Connection> supplier = () -> connection;
            return String.valueOf(NetworkHooks.getConnectionType(supplier));
        } catch (RuntimeException e) {
            return "<error:" + e.getClass().getSimpleName() + ">";
        }
    }

    private static boolean handshakePresent(Connection connection) {
        if (FML_HANDSHAKE_HANDLER == null) {
            return false;
        }
        return connection.channel().attr(FML_HANDSHAKE_HANDLER).get() != null;
    }

    @SuppressWarnings("unchecked")
    private static <T> @Nullable AttributeKey<T> attribute(String name) {
        try {
            Field field = NetworkConstants.class.getDeclaredField(name);
            field.setAccessible(true);
            return (AttributeKey<T>)field.get(null);
        } catch (ReflectiveOperationException e) {
            NliConstants.LOG.warn("[P2P-Netty][forge] Failed to access NetworkConstants.{}", name, e);
            return null;
        }
    }
}
