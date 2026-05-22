package cool.muyucloud.netherlink.client;

import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.mixin.MinecraftAccessor;
import cool.muyucloud.netherlink.p2p.RtcChannel;
import cool.muyucloud.netherlink.p2p.RtcHandshake;
import cool.muyucloud.netherlink.p2p.SignalingClient;
import cool.muyucloud.netherlink.p2p.SignalingMessage;
import dev.onvoid.webrtc.PeerConnectionFactory;
import dev.onvoid.webrtc.RTCConfiguration;
import dev.onvoid.webrtc.RTCIceCandidate;
import dev.onvoid.webrtc.RTCIceServer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.timeout.ReadTimeoutHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.login.LoginProtocols;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.network.EventLoopGroupHolder;
import org.jspecify.annotations.Nullable;

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
        switch (message) {
            case SignalingMessage.FriendJoin.Accepted accepted -> handleAccepted(minecraft, fromPmid, accepted.sessionId());
            case SignalingMessage.FriendJoin.Rejected rejected -> {
                OutgoingJoin join = OUTGOING.get(fromPmid);
                if (join != null && join.sessionId().equals(rejected.sessionId())) {
                    join.result().completeExceptionally(new IllegalStateException("Join request rejected"));
                }
            }
            case SignalingMessage.FriendJoin.Request ignored -> {
            }
            case SignalingMessage.FriendJoin.InviteDeclined ignored -> {
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
                joinHost(minecraft, handshakeResult);
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
        switch (message) {
            case SignalingMessage.WebRtc.Answer answer -> handshake.applyAnswer(answer.sdp()).exceptionally(error -> {
                handshake.abort("answer failed: " + error.getMessage());
                return null;
            });
            case SignalingMessage.WebRtc.IceCandidate ice -> {
                RTCIceCandidate candidate = ice.toRtcIceCandidate();
                handshake.addRemoteIceCandidate(candidate).exceptionally(error -> null);
            }
            case SignalingMessage.WebRtc.Offer ignored -> {
            }
        }
    }

    private static void joinHost(Minecraft minecraft, RtcHandshake.HandshakeResult handshakeResult) {
        minecraft.execute(() -> {
            minecraft.disconnect(new ProgressScreen(true), false);
            Connection connection = connectionFromRtc(handshakeResult);
            LevelLoadTracker tracker = new LevelLoadTracker(0L);
            connection.initiateServerboundPlayConnection(
                "rtc-peer",
                0,
                LoginProtocols.SERVERBOUND,
                LoginProtocols.CLIENTBOUND,
                new ClientHandshakePacketListenerImpl(
                    connection,
                    minecraft,
                    new ServerData("NetherLink", "rtc-peer", ServerData.Type.OTHER),
                    null,
                    false,
                    null,
                    component -> {
                    },
                    tracker,
                    null
                ),
                false
            );
            connection.send(new ServerboundHelloPacket(minecraft.getUser().getName(), minecraft.getUser().getProfileId()));
            ((MinecraftAccessor)minecraft).nli$setPendingConnection(connection);
        });
    }

    private static Connection connectionFromRtc(RtcHandshake.HandshakeResult handshakeResult) {
        Connection connection = new Connection(PacketFlow.CLIENTBOUND);
        Channel channel = new RtcChannel(handshakeResult);
        channel.pipeline().addLast(new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel ch) {
                ChannelPipeline pipeline = ch.pipeline().addLast("timeout", (ChannelHandler)new ReadTimeoutHandler(30));
                Connection.configureSerialization(pipeline, PacketFlow.CLIENTBOUND, false, null);
                connection.configurePacketHandler(pipeline);
            }
        });
        EventLoopGroupHolder.local().eventLoopGroup().register(channel).syncUninterruptibly();
        return connection;
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
