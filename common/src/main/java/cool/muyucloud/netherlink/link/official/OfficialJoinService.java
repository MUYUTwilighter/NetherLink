package cool.muyucloud.netherlink.link.official;

import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.link.bridge.LinkClientConnectionBridge;
import cool.muyucloud.netherlink.link.exception.LinkException;
import cool.muyucloud.netherlink.link.exception.LinkFailures;
import cool.muyucloud.netherlink.link.hook.LinkContextHooks;
import cool.muyucloud.netherlink.link.hook.LinkRuntimeContext;
import cool.muyucloud.netherlink.link.model.*;
import cool.muyucloud.netherlink.link.official.signaling.OfficialSignalingClient;
import cool.muyucloud.netherlink.link.service.LinkJoinService;
import cool.muyucloud.netherlink.link.service.LinkSignalingClient;
import cool.muyucloud.netherlink.link.transport.RtcChannel;
import cool.muyucloud.netherlink.link.transport.RtcHandshake;
import cool.muyucloud.netherlink.link.transport.SignalingException;
import cool.muyucloud.netherlink.link.transport.SignalingMessage;
import dev.onvoid.webrtc.PeerConnectionFactory;
import dev.onvoid.webrtc.RTCConfiguration;
import dev.onvoid.webrtc.RTCIceCandidate;
import dev.onvoid.webrtc.RTCIceServer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public final class OfficialJoinService implements LinkJoinService {
    private static final long JOIN_TIMEOUT_SECONDS = 60L;
    private static final long HANDSHAKE_TIMEOUT_SECONDS = 30L;

    private final ConcurrentHashMap<JoinKey, OutgoingJoin> outgoing = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ClientSession> sessions = new ConcurrentHashMap<>();
    private final Function<String, Object> signalingIdentity;
    private final Function<String, LinkSignalingClient> signalingFactory;
    private @Nullable PeerConnectionFactory factory;

    public OfficialJoinService() {
        this(
            runtimeKey -> LinkContextHooks.require(runtimeKey).account().getMcToken(),
            runtimeKey -> new OfficialSignalingClient(
                LinkContextHooks.require(runtimeKey).account().getMcToken(),
                "NetherLink Client Signaling-" + runtimeKey
            )
        );
    }

    public OfficialJoinService(Function<String, LinkSignalingClient> signalingFactory) {
        this(runtimeKey -> runtimeKey, signalingFactory);
    }

    private OfficialJoinService(
        Function<String, Object> signalingIdentity,
        Function<String, LinkSignalingClient> signalingFactory
    ) {
        this.signalingIdentity = signalingIdentity;
        this.signalingFactory = signalingFactory;
    }

    @Override
    public LinkJoinOperation join(String runtimeKey, LinkJoinTarget target) {
        LinkPeerRoute host = target.route();
        JoinKey key = new JoinKey(runtimeKey, host.presenceId());
        OutgoingJoin existing = this.outgoing.get(key);
        if (existing != null) {
            return existing;
        }

        LinkRuntimeContext context = LinkContextHooks.require(runtimeKey);
        LinkClientConnectionBridge connectionBridge = context.requireClientConnection();
        ClientSession session = this.ensureSession(runtimeKey);
        OutgoingJoin operation = new OutgoingJoin(
            runtimeKey,
            target,
            UUID.randomUUID().toString(),
            connectionBridge,
            session.signaling()
        );
        OutgoingJoin raced = this.outgoing.putIfAbsent(key, operation);
        if (raced != null) {
            return raced;
        }

        operation.completion().whenComplete((ignored1, ignored101) -> {
            this.outgoing.remove(key, operation);
            this.maybeDisconnectSignaling(runtimeKey);
        });
        session.signaling().connect();
        CompletableFuture.delayedExecutor(JOIN_TIMEOUT_SECONDS, TimeUnit.SECONDS).execute(() -> {
            if (!operation.sdpStarted()) {
                operation.fail(new IllegalStateException("Join request timed out"));
            }
        });
        session.signaling().sendClientMessage(host, new SignalingMessage.FriendJoin.Request(operation.sessionId()))
            .whenComplete((ignored2, error) -> {
                if (error != null) {
                    operation.fail(error);
                }
            });
        return operation;
    }

    @Override
    public boolean hasOutgoingJoin(String runtimeKey) {
        return this.outgoing.keySet().stream().anyMatch(key -> key.runtimeKey().equals(runtimeKey));
    }

    @Override
    public synchronized void shutdown(String runtimeKey) {
        this.outgoing.forEach((key, operation) -> {
            if (key.runtimeKey().equals(runtimeKey)) {
                operation.abort();
            }
        });
        ClientSession session = this.sessions.remove(runtimeKey);
        if (session != null) {
            session.signaling().shutdown();
        }
    }

    @Override
    public synchronized void shutdown() {
        new ArrayList<>(this.sessions.keySet()).forEach(this::shutdown);
        this.outgoing.values().forEach(OutgoingJoin::abort);
        this.outgoing.clear();
        if (this.factory != null) {
            this.factory.dispose();
            this.factory = null;
        }
    }

    private synchronized ClientSession ensureSession(String runtimeKey) {
        Object identity = this.signalingIdentity.apply(runtimeKey);
        ClientSession existing = this.sessions.get(runtimeKey);
        if (existing != null && Objects.equals(existing.identity(), identity)) {
            return existing;
        }
        if (existing != null) {
            this.shutdown(runtimeKey);
        }
        LinkSignalingClient signaling = this.signalingFactory.apply(runtimeKey);
        signaling.setFriendJoinHandler((source, message) -> this.handleFriendJoin(runtimeKey, source, message));
        signaling.setWebRtcSignalingHandler((source, message) -> this.handleWebRtc(runtimeKey, source, message));
        signaling.addConnectionListener(new LinkSignalingClient.ConnectionListener() {
            @Override
            public void onSignalingError(@Nullable LinkPeerRoute peer, SignalingException cause) {
                if (peer == null) {
                    OfficialJoinService.this.outgoing.forEach((key, operation) -> {
                        if (key.runtimeKey().equals(runtimeKey)) operation.fail(cause);
                    });
                    return;
                }
                OutgoingJoin operation = OfficialJoinService.this.outgoing.get(new JoinKey(runtimeKey, peer.presenceId()));
                if (operation != null) operation.fail(cause);
            }
        });
        ClientSession created = new ClientSession(identity, signaling);
        this.sessions.put(runtimeKey, created);
        return created;
    }

    private void handleFriendJoin(String runtimeKey, LinkPeerRoute source, SignalingMessage.FriendJoin message) {
        JoinKey key = new JoinKey(runtimeKey, source.presenceId());
        if (message instanceof SignalingMessage.FriendJoin.Accepted accepted) {
            this.handleAccepted(key, source, accepted.sessionId());
        } else if (message instanceof SignalingMessage.FriendJoin.Rejected rejected) {
            OutgoingJoin operation = this.outgoing.get(key);
            if (operation != null && operation.sessionId().equals(rejected.sessionId())) {
                operation.reject();
            }
        }
    }

    private void handleAccepted(JoinKey key, LinkPeerRoute host, String sessionId) {
        OutgoingJoin operation = this.outgoing.get(key);
        if (operation == null || !operation.sessionId().equals(sessionId) || !operation.startSdp()) {
            return;
        }
        ClientSession session = this.sessions.get(key.runtimeKey());
        if (session == null) {
            operation.fail(new IllegalStateException("Signaling client is not connected"));
            return;
        }
        operation.transition(LinkJoinState.NEGOTIATING);
        session.signaling().requestTurnAuth()
            .thenCompose(turn -> this.startHandshake(session.signaling(), host, sessionId, turn, operation))
            .whenComplete((ignored3, error) -> {
                if (error != null) {
                    operation.fail(error);
                }
            });
    }

    private CompletableFuture<Void> startHandshake(
        LinkSignalingClient signaling,
        LinkPeerRoute host,
        String sessionId,
        RTCIceServer turn,
        OutgoingJoin operation
    ) {
        RTCConfiguration config = new RTCConfiguration();
        config.iceServers.add(turn);
        config.portAllocatorConfig.setEnableIpv6(true).setEnableIpv6OnWifi(true);
        RtcHandshake handshake = new RtcHandshake(this.factory(), config, sessionId, true,
            candidate -> signaling.sendClientMessage(host, SignalingMessage.iceCandidate(sessionId, candidate)).exceptionally(ignored4 -> null));
        operation.setHandshake(handshake);
        CompletableFuture.delayedExecutor(HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS).execute(() -> {
            if (!operation.completion().isDone()) {
                handshake.abort("timeout");
            }
        });
        handshake.future().whenComplete((handshakeResult, error) -> {
            if (error != null) {
                operation.fail(error);
                return;
            }
            try {
                operation.transition(LinkJoinState.CONNECTING);
                operation.connectionBridge().join(new RtcChannel(handshakeResult));
                operation.complete();
            } catch (RuntimeException bridgeError) {
                RtcChannel.dispose(handshakeResult);
                operation.fail(bridgeError);
            }
        });
        return handshake.createOffer()
            .thenCompose(offer -> signaling.sendClientMessage(host, new SignalingMessage.WebRtc.Offer(sessionId, offer)));
    }

    private void handleWebRtc(String runtimeKey, LinkPeerRoute source, SignalingMessage.WebRtc message) {
        OutgoingJoin operation = this.outgoing.get(new JoinKey(runtimeKey, source.presenceId()));
        RtcHandshake handshake = operation != null ? operation.handshake() : null;
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
            handshake.addRemoteIceCandidate(candidate).exceptionally(ignored5 -> null);
        }
    }

    private synchronized PeerConnectionFactory factory() {
        if (this.factory == null) {
            this.factory = new PeerConnectionFactory();
        }
        return this.factory;
    }

    private void maybeDisconnectSignaling(String runtimeKey) {
        if (this.hasOutgoingJoin(runtimeKey)) {
            return;
        }
        ClientSession session = this.sessions.get(runtimeKey);
        if (session != null) {
            session.signaling().disconnect();
        }
    }

    private record JoinKey(String runtimeKey, String hostPresenceId) {
    }

    private record ClientSession(Object identity, LinkSignalingClient signaling) {
    }

    private static final class OutgoingJoin implements LinkJoinOperation {
        private final String runtimeKey;
        private final LinkJoinTarget target;
        private final String sessionId;
        private final LinkClientConnectionBridge connectionBridge;
        private final LinkSignalingClient signaling;
        private final CompletableFuture<Void> completion = new CompletableFuture<>();
        private volatile LinkJoinSnapshot snapshot;
        private volatile boolean sdpStarted;
        private volatile RtcHandshake handshake;

        private OutgoingJoin(
            String runtimeKey,
            LinkJoinTarget target,
            String sessionId,
            LinkClientConnectionBridge connectionBridge,
            LinkSignalingClient signaling
        ) {
            this.runtimeKey = runtimeKey;
            this.target = target;
            this.sessionId = sessionId;
            this.connectionBridge = connectionBridge;
            this.signaling = signaling;
            this.snapshot = new LinkJoinSnapshot(runtimeKey, target, LinkJoinState.REQUESTING, null);
        }

        @Override
        public LinkJoinSnapshot snapshot() {
            return this.snapshot;
        }

        @Override
        public CompletableFuture<Void> completion() {
            return this.completion;
        }

        @Override
        public boolean cancel() {
            synchronized (this) {
                if (this.completion.isDone()) {
                    return false;
                }
                if (this.sdpStarted && this.handshake == null) {
                    this.signaling.sendClientMessage(this.target.route(), SignalingMessage.inviteDeclined(this.sessionId)).exceptionally(ignored6 -> null);
                }
                this.snapshot = new LinkJoinSnapshot(this.runtimeKey, this.target, LinkJoinState.CANCELLED, null);
                if (this.handshake != null) {
                    this.handshake.abort("cancelled");
                }
                this.completion.completeExceptionally(new CancellationException("Join cancelled"));
            }
            return true;
        }

        private String sessionId() {
            return this.sessionId;
        }

        private LinkClientConnectionBridge connectionBridge() {
            return this.connectionBridge;
        }

        private boolean sdpStarted() {
            return this.sdpStarted;
        }

        private synchronized boolean startSdp() {
            if (this.sdpStarted || this.completion.isDone()) {
                return false;
            }
            this.sdpStarted = true;
            this.transition(LinkJoinState.ACCEPTED);
            return true;
        }

        private RtcHandshake handshake() {
            return this.handshake;
        }

        private void setHandshake(RtcHandshake handshake) {
            this.handshake = handshake;
        }

        private synchronized void transition(LinkJoinState state) {
            if (!this.completion.isDone()) {
                this.snapshot = new LinkJoinSnapshot(this.runtimeKey, this.target, state, null);
            }
        }

        private synchronized void complete() {
            if (!this.completion.isDone()) {
                this.snapshot = new LinkJoinSnapshot(this.runtimeKey, this.target, LinkJoinState.CONNECTED, null);
                this.completion.complete(null);
            }
        }

        private synchronized void reject() {
            if (!this.completion.isDone()) {
                LinkException error = new LinkException(new LinkFailure(LinkFailureCode.FORBIDDEN, "Join request rejected", false));
                this.snapshot = new LinkJoinSnapshot(this.runtimeKey, this.target, LinkJoinState.REJECTED, LinkFailures.from(error));
                this.completion.completeExceptionally(error);
            }
        }

        private synchronized void fail(Throwable error) {
            if (!this.completion.isDone()) {
                var failure = LinkFailures.from(error);
                this.snapshot = new LinkJoinSnapshot(this.runtimeKey, this.target, LinkJoinState.FAILED, failure);
                this.completion.completeExceptionally(error);
                NliConstants.LOG.warn("[P2P][{}] Join failed session={}: {}", this.runtimeKey, this.sessionId, failure.message());
            }
        }

        private synchronized void abort() {
            String reason = "shutdown";
            if (this.handshake != null) {
                this.handshake.abort(reason);
            }
            if (!this.completion.isDone()) {
                LinkFailure failure = new LinkFailure(LinkFailureCode.CANCELLED, reason, false);
                this.snapshot = new LinkJoinSnapshot(this.runtimeKey, this.target, LinkJoinState.CANCELLED, failure);
                this.completion.completeExceptionally(new CancellationException(reason));
            }
        }
    }
}
