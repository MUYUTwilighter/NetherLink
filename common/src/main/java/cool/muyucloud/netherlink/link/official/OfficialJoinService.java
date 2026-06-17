package cool.muyucloud.netherlink.link.official;

import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.account.MinecraftAccount;
import cool.muyucloud.netherlink.link.LinkClientContext;
import cool.muyucloud.netherlink.link.LinkFriendEntry;
import cool.muyucloud.netherlink.link.LinkJoinService;
import cool.muyucloud.netherlink.link.LinkSignalingClient;
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

public final class OfficialJoinService implements LinkJoinService {
    private static final long JOIN_TIMEOUT_SECONDS = 60L;
    private static final long HANDSHAKE_TIMEOUT_SECONDS = 30L;

    private final ConcurrentHashMap<UUID, OutgoingJoin> outgoing = new ConcurrentHashMap<>();
    private @Nullable PeerConnectionFactory factory;
    private @Nullable LinkSignalingClient signaling;
    private @Nullable MinecraftAccount account;

    @Override
    public CompletableFuture<Void> join(LinkClientContext context, LinkFriendEntry target) {
        UUID hostPresenceId = target.presenceId();
        if (hostPresenceId == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Selected friend has no joinable presence"));
        }
        Minecraft minecraft = context.minecraft();
        if (minecraft.level != null || minecraft.getSingleplayerServer() != null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Join requests are only available from the main menu"));
        }
        OutgoingJoin existing = this.outgoing.get(hostPresenceId);
        if (existing != null) {
            return existing.result();
        }
        this.ensureSignaling(context);
        LinkSignalingClient client = this.signaling;
        if (client == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Signaling client was not created"));
        }
        String sessionId = UUID.randomUUID().toString();
        CompletableFuture<Void> result = new CompletableFuture<>();
        OutgoingJoin join = new OutgoingJoin(sessionId, result);
        OutgoingJoin raced = this.outgoing.putIfAbsent(hostPresenceId, join);
        if (raced != null) {
            return raced.result();
        }
        result.whenComplete((ignored, error) -> {
            this.outgoing.remove(hostPresenceId, join);
            this.maybeDisconnectSignaling();
        });
        client.connect();
        CompletableFuture.delayedExecutor(JOIN_TIMEOUT_SECONDS, TimeUnit.SECONDS).execute(() -> {
            if (!join.sdpStarted() && result.completeExceptionally(new IllegalStateException("Join request timed out"))) {
                NliConstants.LOG.warn("[P2P][client] Join request timed out session={}", sessionId);
            }
        });
        client.sendClientMessage(hostPresenceId, new SignalingMessage.FriendJoin.Request(sessionId)).whenComplete((ignored, error) -> {
            if (error != null) {
                result.completeExceptionally(error);
            }
        });
        return result;
    }

    @Override
    public boolean hasOutgoingJoin() {
        return !this.outgoing.isEmpty();
    }

    @Override
    public void shutdown() {
        this.outgoing.values().forEach(join -> join.result().completeExceptionally(new IllegalStateException("shutdown")));
        this.outgoing.clear();
        if (this.signaling != null) {
            this.signaling.shutdown();
            this.signaling = null;
        }
        if (this.factory != null) {
            this.factory.dispose();
            this.factory = null;
        }
        this.account = null;
    }

    private void ensureSignaling(LinkClientContext context) {
        MinecraftAccount current = context.account();
        MinecraftAccount existing = this.account;
        String currentToken = current.getMcToken();
        if (this.signaling != null && existing != null && currentToken != null && currentToken.equals(existing.getMcToken())) {
            return;
        }
        this.shutdown();
        this.account = current;
        this.signaling = new SignalingClient(context.account().getMcToken(), "NetherLink Client Signaling");
        this.signaling.setFriendJoinHandler((fromPresenceId, message) -> this.handleFriendJoin(context.minecraft(), fromPresenceId, message));
        this.signaling.setWebRtcSignalingHandler((fromPresenceId, message) -> this.handleWebRtc(context.minecraft(), fromPresenceId, message));
    }

    private void handleFriendJoin(Minecraft minecraft, UUID fromPresenceId, SignalingMessage.FriendJoin message) {
        switch (message) {
            case SignalingMessage.FriendJoin.Accepted accepted -> this.handleAccepted(minecraft, fromPresenceId, accepted.sessionId());
            case SignalingMessage.FriendJoin.Rejected rejected -> {
                OutgoingJoin join = this.outgoing.get(fromPresenceId);
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

    private void handleAccepted(Minecraft minecraft, UUID hostPresenceId, String sessionId) {
        OutgoingJoin join = this.outgoing.get(hostPresenceId);
        if (join == null || !join.sessionId().equals(sessionId) || !join.startSdp()) {
            return;
        }
        LinkSignalingClient client = this.signaling;
        if (client == null) {
            join.result().completeExceptionally(new IllegalStateException("Signaling client is not connected"));
            return;
        }
        client.requestTurnAuth().thenCompose(turn -> this.startHandshake(minecraft, client, hostPresenceId, sessionId, turn, join))
            .whenComplete((ignored, error) -> {
                if (error != null) {
                    join.result().completeExceptionally(error);
                }
            });
    }

    private CompletableFuture<Void> startHandshake(Minecraft minecraft, LinkSignalingClient client, UUID hostPresenceId, String sessionId, RTCIceServer turn, OutgoingJoin join) {
        RTCConfiguration config = new RTCConfiguration();
        config.iceServers.add(turn);
        config.portAllocatorConfig.setEnableIpv6(true).setEnableIpv6OnWifi(true);
        RtcHandshake handshake = new RtcHandshake(this.factory(), config, sessionId, true,
            candidate -> client.sendClientMessage(hostPresenceId, SignalingMessage.iceCandidate(sessionId, candidate)).exceptionally(error -> null));
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
            .thenCompose(offer -> client.sendClientMessage(hostPresenceId, new SignalingMessage.WebRtc.Offer(sessionId, offer)));
    }

    private void handleWebRtc(Minecraft minecraft, UUID fromPresenceId, SignalingMessage.WebRtc message) {
        OutgoingJoin join = this.outgoing.get(fromPresenceId);
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

    private PeerConnectionFactory factory() {
        if (this.factory == null) {
            this.factory = new PeerConnectionFactory();
        }
        return this.factory;
    }

    private void maybeDisconnectSignaling() {
        if (this.outgoing.isEmpty() && this.signaling != null) {
            this.signaling.disconnect();
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
