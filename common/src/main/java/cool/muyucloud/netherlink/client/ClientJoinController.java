package cool.muyucloud.netherlink.client;

import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.access.MinecraftConnectionAccess;
import cool.muyucloud.netherlink.p2p.RtcChannel;
import cool.muyucloud.netherlink.p2p.RtcHandshake;
import cool.muyucloud.netherlink.p2p.SignalingClient;
import cool.muyucloud.netherlink.p2p.SignalingMessage;
import dev.onvoid.webrtc.PeerConnectionFactory;
import dev.onvoid.webrtc.RTCConfiguration;
import dev.onvoid.webrtc.RTCIceCandidate;
import dev.onvoid.webrtc.RTCIceServer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.handler.timeout.ReadTimeoutHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class ClientJoinController {
    private static final long JOIN_TIMEOUT_SECONDS = 60L;
    private static final long HANDSHAKE_TIMEOUT_SECONDS = 30L;
    private static final ConcurrentHashMap<UUID, OutgoingJoin> OUTGOING = new ConcurrentHashMap<>();
    private static @Nullable PeerConnectionFactory factory;
    private static @Nullable SignalingClient signaling;
    private static @Nullable LauncherSessionAccount account;

    private ClientJoinController() {
    }

    public static CompletableFuture<Void> join(Minecraft minecraft, UUID hostPmid) {
        if (minecraft.level != null || minecraft.getSingleplayerServer() != null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Join requests are only available from the main menu"));
        }
        OutgoingJoin existing = OUTGOING.get(hostPmid);
        if (existing != null) {
            return existing.result();
        }
        ensureSignaling(minecraft);
        SignalingClient client = signaling;
        if (client == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Signaling client was not created"));
        }
        String sessionId = UUID.randomUUID().toString();
        CompletableFuture<Void> result = new CompletableFuture<>();
        OutgoingJoin join = new OutgoingJoin(sessionId, result);
        OutgoingJoin raced = OUTGOING.putIfAbsent(hostPmid, join);
        if (raced != null) {
            return raced.result();
        }
        result.whenComplete((ignored, error) -> {
            OUTGOING.remove(hostPmid, join);
            maybeDisconnectSignaling();
        });
        client.connect();
        CompletableFuture.delayedExecutor(JOIN_TIMEOUT_SECONDS, TimeUnit.SECONDS).execute(() -> {
            if (!join.sdpStarted() && result.completeExceptionally(new IllegalStateException("Join request timed out"))) {
                NliConstants.LOG.warn("[P2P][client] Join request timed out session={}", sessionId);
            }
        });
        client.sendClientMessage(hostPmid, new SignalingMessage.FriendJoin.Request(sessionId)).whenComplete((ignored, error) -> {
            if (error != null) {
                result.completeExceptionally(error);
            }
        });
        return result;
    }

    public static boolean hasOutgoingJoin() {
        return !OUTGOING.isEmpty();
    }

    public static void shutdown() {
        OUTGOING.values().forEach(join -> join.result().completeExceptionally(new IllegalStateException("shutdown")));
        OUTGOING.clear();
        if (signaling != null) {
            signaling.shutdown();
            signaling = null;
        }
        if (factory != null) {
            factory.dispose();
            factory = null;
        }
        account = null;
    }

    private static void ensureSignaling(Minecraft minecraft) {
        LauncherSessionAccount current = new LauncherSessionAccount(minecraft.getUser());
        if (signaling != null && account != null && current.getMcToken().equals(account.getMcToken())) {
            return;
        }
        shutdown();
        account = current;
        signaling = new SignalingClient(current.getMcToken(), "NetherLink Client Signaling");
        signaling.setFriendJoinHandler((fromPmid, message) -> handleFriendJoin(minecraft, fromPmid, message));
        signaling.setWebRtcSignalingHandler((fromPmid, message) -> handleWebRtc(minecraft, fromPmid, message));
    }

    private static void handleFriendJoin(Minecraft minecraft, UUID fromPmid, SignalingMessage.FriendJoin message) {
        if (message instanceof SignalingMessage.FriendJoin.Accepted accepted) {
            handleAccepted(minecraft, fromPmid, accepted.sessionId());
        } else if (message instanceof SignalingMessage.FriendJoin.Rejected rejected) {
            OutgoingJoin join = OUTGOING.get(fromPmid);
            if (join != null && join.sessionId().equals(rejected.sessionId())) {
                join.result().completeExceptionally(new IllegalStateException("Join request rejected"));
            }
        }
    }

    private static void handleAccepted(Minecraft minecraft, UUID hostPmid, String sessionId) {
        OutgoingJoin join = OUTGOING.get(hostPmid);
        if (join == null || !join.sessionId().equals(sessionId) || !join.startSdp()) {
            return;
        }
        SignalingClient client = signaling;
        if (client == null) {
            join.result().completeExceptionally(new IllegalStateException("Signaling client is not connected"));
            return;
        }
        client.requestTurnAuth().thenCompose(turn -> startHandshake(minecraft, client, hostPmid, sessionId, turn, join))
            .whenComplete((ignored, error) -> {
                if (error != null) {
                    join.result().completeExceptionally(error);
                }
            });
    }

    private static CompletableFuture<Void> startHandshake(Minecraft minecraft, SignalingClient client, UUID hostPmid, String sessionId, RTCIceServer turn, OutgoingJoin join) {
        RTCConfiguration config = new RTCConfiguration();
        config.iceServers.add(turn);
        config.portAllocatorConfig.setEnableIpv6(true).setEnableIpv6OnWifi(true);
        RtcHandshake handshake = new RtcHandshake(factory(), config, sessionId, true,
            candidate -> client.sendClientMessage(hostPmid, SignalingMessage.iceCandidate(sessionId, candidate)).exceptionally(error -> null));
        join.setHandshake(handshake);
        CompletableFuture.delayedExecutor(HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS).execute(() -> {
            if (!join.result().isDone()) {
                handshake.abort("timeout");
            }
        });
        handshake.future().whenComplete((handshakeResult, error) -> {
            if (error != null) {
                join.result().completeExceptionally(error);
            } else {
                joinHost(minecraft, handshake.id(), handshakeResult);
                join.result().complete(null);
            }
        });
        return handshake.createOffer()
            .thenCompose(offer -> client.sendClientMessage(hostPmid, new SignalingMessage.WebRtc.Offer(sessionId, offer)));
    }

    private static void handleWebRtc(Minecraft minecraft, UUID fromPmid, SignalingMessage.WebRtc message) {
        OutgoingJoin join = OUTGOING.get(fromPmid);
        RtcHandshake handshake = join != null ? join.handshake() : null;
        if (handshake == null || !handshake.id().equals(message.sessionId())) {
            return;
        }
        if (message instanceof SignalingMessage.WebRtc.Answer answer) {
            handshake.applyAnswer(answer.sdp()).exceptionally(error -> {
                handshake.abort("answer failed: " + error.getMessage());
                return null;
            });
        } else if (message instanceof SignalingMessage.WebRtc.IceCandidate ice) {
            RTCIceCandidate candidate = ice.toRtcIceCandidate();
            handshake.addRemoteIceCandidate(candidate).exceptionally(error -> null);
        }
    }

    private static void joinHost(Minecraft minecraft, String sessionId, RtcHandshake.HandshakeResult handshakeResult) {
        minecraft.execute(() -> {
            NliConstants.LOG.info("[P2P][client][{}] Preparing Minecraft login over RTC", sessionId);
            minecraft.clearLevel(new ProgressScreen(true));
            RtcConnection rtcConnection = connectionFromRtc(sessionId, handshakeResult);
            Connection connection = rtcConnection.connection();
            registerLoaderClientLoginChannel(connection);
            NliConstants.LOG.info(
                "[P2P][client][{}] Created pending connection local={} remote={} active={} open={}",
                sessionId,
                rtcConnection.channel().localAddress(),
                rtcConnection.channel().remoteAddress(),
                rtcConnection.channel().isActive(),
                rtcConnection.channel().isOpen()
            );
            connection.setListener(new ClientHandshakePacketListenerImpl(
                connection,
                minecraft,
                new ServerData("NetherLink", "rtc-peer", false),
                null,
                false,
                null,
                component -> {
                }
            ));
            ((MinecraftConnectionAccess)minecraft).nli$setPendingConnection(connection);
            NliConstants.LOG.info("[P2P][client][{}] Pending connection installed, scheduling login packets", sessionId);
            rtcConnection.channel().eventLoop().execute(() -> {
                try {
                    NliConstants.LOG.info("[P2P][client][{}] Sending ClientIntentionPacket while protocol is handshaking", sessionId);
                    connection.send(new ClientIntentionPacket("rtc-peer", 0, ConnectionProtocol.LOGIN));
                    NliConstants.LOG.info(
                        "[P2P][client][{}] Sending ServerboundHelloPacket name={} profile={}",
                        sessionId,
                        minecraft.getUser().getName(),
                        minecraft.getUser().getProfileId()
                    );
                    connection.send(new ServerboundHelloPacket(minecraft.getUser().getName(), Optional.ofNullable(minecraft.getUser().getProfileId())));
                } catch (Throwable error) {
                    NliConstants.LOG.error("[P2P][client][{}] Failed while starting Minecraft login over RTC", sessionId, error);
                    throw error;
                }
            });
        });
    }

    private static RtcConnection connectionFromRtc(String sessionId, RtcHandshake.HandshakeResult handshakeResult) {
        Connection connection = new Connection(PacketFlow.CLIENTBOUND);
        Channel channel = new RtcChannel(handshakeResult);
        channel.pipeline().addLast(new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel ch) {
                ChannelPipeline pipeline = ch.pipeline()
                    .addLast("nli_diagnostics", new ClientDiagnosticsHandler(sessionId, connection))
                    .addLast("timeout", (ChannelHandler)new ReadTimeoutHandler(30));
                Connection.configureSerialization(pipeline, PacketFlow.CLIENTBOUND);
                pipeline.addLast("nli_packet_diagnostics", new ClientPacketDiagnosticsHandler(sessionId, connection));
                pipeline.addLast("packet_handler", connection);
            }
        });
        Connection.LOCAL_WORKER_GROUP.get().register(channel).syncUninterruptibly();
        return new RtcConnection(connection, channel);
    }

    private static void registerLoaderClientLoginChannel(Connection connection) {
        try {
            Class<?> hooks = Class.forName("net.minecraftforge.network.NetworkHooks");
            hooks.getMethod("registerClientLoginChannel", Connection.class).invoke(null, connection);
            NliConstants.LOG.info("[P2P][client] Registered Forge client login channel");
        } catch (ClassNotFoundException ignored) {
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to register loader client login channel", e);
        }
    }

    private static PeerConnectionFactory factory() {
        if (factory == null) {
            factory = new PeerConnectionFactory();
        }
        return factory;
    }

    private static void maybeDisconnectSignaling() {
        if (OUTGOING.isEmpty() && signaling != null) {
            signaling.disconnect();
        }
    }

    private record RtcConnection(Connection connection, Channel channel) {
    }

    private static final class ClientPacketDiagnosticsHandler extends ChannelDuplexHandler {
        private final String sessionId;
        private final Connection connection;

        private ClientPacketDiagnosticsHandler(String sessionId, Connection connection) {
            this.sessionId = sessionId;
            this.connection = connection;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (shouldLog(ctx)) {
                NliConstants.LOG.info(
                    "[P2P][client][{}] IN packet={} protocol={} listener={}",
                    this.sessionId,
                    msg.getClass().getName(),
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
                    "[P2P][client][{}] OUT packet={} protocol={} listener={}",
                    this.sessionId,
                    msg.getClass().getName(),
                    protocolName(ctx),
                    listenerName()
                );
            }
            super.write(ctx, msg, promise);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            NliConstants.LOG.error(
                "[P2P][client][{}] Packet pipeline exception protocol={} listener={}",
                this.sessionId,
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

    private static final class ClientDiagnosticsHandler extends ChannelInboundHandlerAdapter {
        private final String sessionId;
        private final Connection connection;

        private ClientDiagnosticsHandler(String sessionId, Connection connection) {
            this.sessionId = sessionId;
            this.connection = connection;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            NliConstants.LOG.info("[P2P][client][{}] Netty channelActive listener={}", this.sessionId, listenerName());
            super.channelActive(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            NliConstants.LOG.info("[P2P][client][{}] Netty channelInactive listener={}", this.sessionId, listenerName());
            super.channelInactive(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            NliConstants.LOG.error("[P2P][client][{}] Netty pipeline exception listener={}", this.sessionId, listenerName(), cause);
            super.exceptionCaught(ctx, cause);
        }

        private String listenerName() {
            return this.connection.getPacketListener() == null ? "<null>" : this.connection.getPacketListener().getClass().getName();
        }
    }

    private static final class OutgoingJoin {
        private final String sessionId;
        private final CompletableFuture<Void> result;
        private volatile boolean sdpStarted;
        private volatile RtcHandshake handshake;

        private OutgoingJoin(String sessionId, CompletableFuture<Void> result) {
            this.sessionId = sessionId;
            this.result = result;
        }

        private String sessionId() {
            return this.sessionId;
        }

        private CompletableFuture<Void> result() {
            return this.result;
        }

        private boolean sdpStarted() {
            return this.sdpStarted;
        }

        private synchronized boolean startSdp() {
            if (this.sdpStarted) {
                return false;
            }
            this.sdpStarted = true;
            return true;
        }

        private RtcHandshake handshake() {
            return this.handshake;
        }

        private void setHandshake(RtcHandshake handshake) {
            this.handshake = handshake;
        }
    }
}
