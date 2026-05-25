package cool.muyucloud.netherlink.p2p;

import cool.muyucloud.netherlink.NliConstants;
import io.netty.channel.*;
import io.netty.handler.timeout.ReadTimeoutHandler;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.RateKickingConnection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerConnectionListener;
import net.minecraft.server.network.ServerHandshakePacketListenerImpl;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.UUID;

public final class ServerChannelAcceptor {
    private static final @Nullable Method SET_INTENDED_PROFILE_ID = findSetIntendedProfileId();

    private ServerChannelAcceptor() {
    }

    public static void accept(MinecraftServer server, Channel channel, @Nullable UUID profileId) {
        ServerConnectionListener listener = server.getConnection();
        NliConstants.LOG.info(
            "[P2P-Netty][server] Accepting RTC channel profile={} local={} remote={} active={} open={}",
            profileId,
            channel.localAddress(),
            channel.remoteAddress(),
            channel.isActive(),
            channel.isOpen()
        );
        channel.pipeline().addLast(new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel ch) {
                int rateLimitPacketsPerSecond = server.getRateLimitPacketsPerSecond();
                Connection connection = rateLimitPacketsPerSecond > 0
                    ? new RateKickingConnection(rateLimitPacketsPerSecond)
                    : new Connection(PacketFlow.SERVERBOUND);
                ChannelPipeline pipeline = ch.pipeline()
                    .addLast("nli_diagnostics", new ServerDiagnosticsHandler(profileId, connection))
                    .addLast("timeout", (ChannelHandler)new ReadTimeoutHandler(30));
                Connection.configureSerialization(pipeline, PacketFlow.SERVERBOUND);
                pipeline.addLast("nli_loader_login", new ServerLoaderLoginRegistrationHandler(profileId, connection));
                pipeline.addLast("nli_packet_diagnostics", new ServerPacketDiagnosticsHandler(profileId, connection));
                pipeline.addLast("packet_handler", connection);
                connection.setListener(new ServerHandshakePacketListenerImpl(server, connection));
                NliConstants.LOG.info(
                    "[P2P-Netty][server] Server connection listener installed profile={} listener={}",
                    profileId,
                    connection.getPacketListener() == null ? "<null>" : connection.getPacketListener().getClass().getName()
                );
                setIntendedProfileId(connection, profileId);
                listener.getConnections().add(connection);
                NliConstants.LOG.info("[P2P-Netty][server] RTC connection added to server list profile={}", profileId);
            }
        });
        LoaderNetworkEventLoops.serverGroup().register(channel).syncUninterruptibly();
    }

    private static void setIntendedProfileId(Connection connection, @Nullable UUID profileId) {
        if (SET_INTENDED_PROFILE_ID == null || profileId == null) {
            NliConstants.LOG.info(
                "[P2P-Netty][server] Skipping intended profile id methodPresent={} profile={}",
                SET_INTENDED_PROFILE_ID != null,
                profileId
            );
            return;
        }
        try {
            NliConstants.LOG.info("[P2P-Netty][server] Setting intended profile id {}", profileId);
            SET_INTENDED_PROFILE_ID.invoke(connection, profileId);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to set intended profile id", e);
        }
    }

    private static @Nullable Method findSetIntendedProfileId() {
        try {
            return Connection.class.getMethod("setIntendedProfileId", UUID.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static final class ServerLoaderLoginRegistrationHandler extends ChannelInboundHandlerAdapter {
        private final @Nullable UUID profileId;
        private final Connection connection;
        private boolean registered;

        private ServerLoaderLoginRegistrationHandler(@Nullable UUID profileId, Connection connection) {
            this.profileId = profileId;
            this.connection = connection;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (!this.registered && msg instanceof ClientIntentionPacket packet) {
                this.registered = true;
                LoaderNetworkDiagnostics.logServerIntention(this.connection, packet, "before-server-login-channel", this.profileId);
                registerLoaderServerLoginChannel(this.connection, packet, this.profileId);
                LoaderNetworkDiagnostics.logServerIntention(this.connection, packet, "after-server-login-channel", this.profileId);
            }
            super.channelRead(ctx, msg);
        }
    }

    private static void registerLoaderServerLoginChannel(Connection connection, ClientIntentionPacket packet, @Nullable UUID profileId) {
        try {
            Class<?> hooks = Class.forName("net.minecraftforge.network.NetworkHooks");
            hooks.getMethod("registerServerLoginChannel", Connection.class, ClientIntentionPacket.class).invoke(null, connection, packet);
            NliConstants.LOG.info("[P2P-Netty][server] Registered Forge server login channel profile={}", profileId);
        } catch (ClassNotFoundException ignored) {
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to register loader server login channel", e);
        }
    }

    private static final class ServerPacketDiagnosticsHandler extends ChannelDuplexHandler {
        private final @Nullable UUID profileId;
        private final Connection connection;

        private ServerPacketDiagnosticsHandler(@Nullable UUID profileId, Connection connection) {
            this.profileId = profileId;
            this.connection = connection;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (shouldLog(ctx)) {
                NliConstants.LOG.info(
                    "[P2P-Netty][server] IN packet={} profile={} protocol={} listener={}",
                    msg.getClass().getName(),
                    this.profileId,
                    protocolName(ctx),
                    listenerName()
                );
            }
            super.channelRead(ctx, msg);
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (shouldLog(ctx)) {
                NliConstants.LOG.info(
                    "[P2P-Netty][server] OUT packet={} profile={} protocol={} listener={}",
                    msg.getClass().getName(),
                    this.profileId,
                    protocolName(ctx),
                    listenerName()
                );
            }
            super.write(ctx, msg, promise);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            NliConstants.LOG.error(
                "[P2P-Netty][server] packet pipeline exception profile={} protocol={} listener={}",
                this.profileId,
                protocolName(ctx),
                listenerName(),
                cause
            );
            super.exceptionCaught(ctx, cause);
        }

        private static boolean shouldLog(ChannelHandlerContext ctx) {
            return ctx.channel().attr(Connection.ATTRIBUTE_PROTOCOL).get() != ConnectionProtocol.PLAY;
        }

        private static String protocolName(ChannelHandlerContext ctx) {
            ConnectionProtocol protocol = ctx.channel().attr(Connection.ATTRIBUTE_PROTOCOL).get();
            return protocol == null ? "<null>" : protocol.toString();
        }

        private String listenerName() {
            return this.connection.getPacketListener() == null ? "<null>" : this.connection.getPacketListener().getClass().getName();
        }
    }

    private static final class ServerDiagnosticsHandler extends ChannelInboundHandlerAdapter {
        private final @Nullable UUID profileId;
        private final Connection connection;

        private ServerDiagnosticsHandler(@Nullable UUID profileId, Connection connection) {
            this.profileId = profileId;
            this.connection = connection;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            NliConstants.LOG.info("[P2P-Netty][server] channelActive profile={} listener={}", this.profileId, listenerName());
            super.channelActive(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            NliConstants.LOG.info("[P2P-Netty][server] channelInactive profile={} listener={}", this.profileId, listenerName());
            super.channelInactive(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            NliConstants.LOG.error("[P2P-Netty][server] pipeline exception profile={} listener={}", this.profileId, listenerName(), cause);
            super.exceptionCaught(ctx, cause);
        }

        private String listenerName() {
            return this.connection.getPacketListener() == null ? "<null>" : this.connection.getPacketListener().getClass().getName();
        }
    }
}
