package cool.muyucloud.netherlink.link.transport;

import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.link.bridge.LinkServerConnectionBridge;
import cool.muyucloud.netherlink.link.model.LinkPeerRoute;
import cool.muyucloud.netherlink.link.service.LinkSignalingClient;
import dev.onvoid.webrtc.PeerConnectionFactory;
import dev.onvoid.webrtc.RTCConfiguration;
import dev.onvoid.webrtc.RTCIceCandidate;
import dev.onvoid.webrtc.RTCIceServer;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Internal host-side coordinator that accepts authorized join signaling, negotiates WebRTC, and
 * hands established channels to a server bridge. One manager belongs to exactly one account
 * runtime and signaling identity.
 */
public final class ServerP2PManager {
    private static final long SIGNALING_RECONNECT_DELAY_SECONDS = 1L;
    private static final long HANDSHAKE_TIMEOUT_SECONDS = 30L;

    private final String accountName;
    private final LinkServerConnectionBridge connectionBridge;
    private final LinkSignalingClient signaling;
    private final ConcurrentHashMap<String, UUID> profileIdsByPresence = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> acceptedAwaitingOffer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RtcHandshake> handshakes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<PendingIceKey, List<RTCIceCandidate>> pendingIceCandidates = new ConcurrentHashMap<>();
    private final LinkSignalingClient.ConnectionListener connectionListener = new LinkSignalingClient.ConnectionListener() {
        @Override
        public void onSignalingConnected() {
            ServerP2PManager.this.onSignalingConnected();
        }

        @Override
        public void onSignalingError(@Nullable LinkPeerRoute peer, SignalingException cause) {
            if (cause instanceof SignalingException.SignalingAuthException) {
                ServerP2PManager.this.onSignalingAuthFailed(cause);
                return;
            }
            if (peer != null) {
                RtcHandshake handshake = ServerP2PManager.this.handshakes.get(peer.presenceId());
                if (handshake != null) {
                    handshake.abort("signaling error: " + cause.getClass().getSimpleName());
                }
            }
        }

        @Override
        public void onSignalingDisconnected() {
            ServerP2PManager.this.onSignalingDisconnected();
        }

        @Override
        public void onSignalingConnectFailed() {
            ServerP2PManager.this.onSignalingConnectFailed();
        }
    };
    private @Nullable PeerConnectionFactory factory;
    private volatile CompletableFuture<Void> signalingReady = new CompletableFuture<>();
    private volatile SignalingException.SignalingAuthException signalingAuthFailure;
    private volatile boolean shutdown;

    public ServerP2PManager(String accountName, LinkServerConnectionBridge connectionBridge, LinkSignalingClient signaling) {
        this.accountName = accountName;
        this.connectionBridge = connectionBridge;
        this.signaling = signaling;
        this.signaling.setFriendJoinHandler(this::handleFriendJoin);
        this.signaling.setWebRtcSignalingHandler(this::handleWebRtc);
        this.signaling.addConnectionListener(this.connectionListener);
    }

    public void start() {
        this.shutdown = false;
        this.signalingAuthFailure = null;
        NliConstants.LOG.info("[P2P][{}] Starting server P2P manager", this.accountName);
        this.signaling.connect();
        this.warmupTurnAuth();
    }

    public CompletableFuture<Void> awaitSignalingReady(Duration timeout) {
        SignalingException.SignalingAuthException authFailure = this.signalingAuthFailure;
        if (authFailure != null) {
            return CompletableFuture.failedFuture(authFailure);
        }
        CompletableFuture<Void> result = new CompletableFuture<>();
        this.signalingReady.whenComplete((_, error) -> {
            if (error != null) {
                result.completeExceptionally(error);
            } else {
                result.complete(null);
            }
        });
        result.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
        return result;
    }

    public void updatePresence(java.util.Map<String, UUID> profileIdsByPresence) {
        this.profileIdsByPresence.clear();
        this.profileIdsByPresence.putAll(profileIdsByPresence);
        NliConstants.LOG.info("[P2P][{}] Updated presence peer map: {} entries", this.accountName, profileIdsByPresence.size());
    }

    public synchronized void shutdown() {
        this.shutdown = true;
        NliConstants.LOG.info("[P2P][{}] Shutting down server P2P manager", this.accountName);
        this.signaling.removeConnectionListener(this.connectionListener);
        this.handshakes.values().forEach(handshake -> handshake.abort("shutdown"));
        this.handshakes.clear();
        this.acceptedAwaitingOffer.clear();
        this.pendingIceCandidates.clear();
        if (this.factory != null) {
            this.factory.dispose();
            this.factory = null;
        }
        this.signaling.shutdown();
    }

    private void onSignalingDisconnected() {
        if (!this.shutdown) {
            if (this.signalingReady.isDone()) {
                this.signalingReady = new CompletableFuture<>();
            }
            NliConstants.LOG.warn("[P2P][{}] Signaling disconnected, reconnecting in {}s", this.accountName, SIGNALING_RECONNECT_DELAY_SECONDS);
            CompletableFuture.delayedExecutor(SIGNALING_RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS).execute(() -> {
                if (!this.shutdown) {
                    this.signaling.connect();
                    this.warmupTurnAuth();
                }
            });
        }
    }

    private void onSignalingConnected() {
        NliConstants.LOG.info("[P2P][{}] Signaling ready", this.accountName);
        this.signalingAuthFailure = null;
        this.signalingReady.complete(null);
    }

    private void onSignalingConnectFailed() {
        if (!this.shutdown) {
            this.onSignalingDisconnected();
        }
    }

    private void onSignalingAuthFailed(SignalingException cause) {
        if (this.shutdown) {
            return;
        }
        SignalingException.SignalingAuthException authFailure = cause instanceof SignalingException.SignalingAuthException typed
            ? typed
            : new SignalingException.SignalingAuthException(cause.getMessage());
        this.signalingAuthFailure = authFailure;
        this.signalingReady.completeExceptionally(authFailure);
    }

    private void warmupTurnAuth() {
        this.signaling.requestTurnAuth().whenComplete((_, error) -> {
            if (error != null) {
                NliConstants.LOG.warn("[P2P][{}] TURN auth warmup failed: {}", this.accountName, error.toString());
            } else {
                NliConstants.LOG.info("[P2P][{}] TURN auth warmup completed", this.accountName);
            }
        });
    }

    private void handleFriendJoin(LinkPeerRoute source, SignalingMessage.FriendJoin message) {
        NliConstants.LOG.info("[P2P][{}] Received friend join message {} from {}", this.accountName, message.getClass().getSimpleName(), source.presenceId());
        switch (message) {
            case SignalingMessage.FriendJoin.Request request -> this.acceptJoinRequest(source, request.sessionId());
            case SignalingMessage.FriendJoin.InviteDeclined ignored -> this.acceptedAwaitingOffer.remove(source.presenceId());
            case SignalingMessage.FriendJoin.Accepted ignored -> {
            }
            case SignalingMessage.FriendJoin.Rejected ignored -> {
            }
        }
    }

    private void acceptJoinRequest(LinkPeerRoute source, String sessionId) {
        UUID profileId = this.profileIdFor(source);
        if (profileId == null) {
            NliConstants.LOG.warn(
                "[P2P][{}] Accepting join request from unknown PMID {}; server login authentication will verify the player",
                this.accountName,
                source.presenceId()
            );
        } else {
            NliConstants.LOG.info("[P2P][{}] Accepting join request session={} presence={} profile={}", this.accountName, sessionId, source.presenceId(), profileId);
        }
        this.acceptedAwaitingOffer.put(source.presenceId(), sessionId);
        this.signaling.sendClientMessage(source, SignalingMessage.joinAccepted(sessionId)).exceptionally(error -> {
            this.acceptedAwaitingOffer.remove(source.presenceId(), sessionId);
            NliConstants.LOG.warn("[P2P][{}] Failed to accept join request {}: {}", this.accountName, sessionId, error.getMessage());
            return null;
        });
    }

    private void handleWebRtc(LinkPeerRoute source, SignalingMessage.WebRtc message) {
        NliConstants.LOG.info("[P2P][{}] Received WebRTC message {} session={} from {}", this.accountName, message.getClass().getSimpleName(), message.sessionId(), source.presenceId());
        switch (message) {
            case SignalingMessage.WebRtc.Offer offer -> this.handleOffer(source, offer);
            case SignalingMessage.WebRtc.IceCandidate ice -> this.handleIceCandidate(source, ice);
            case SignalingMessage.WebRtc.Answer ignored -> {
            }
        }
    }

    private void handleOffer(LinkPeerRoute source, SignalingMessage.WebRtc.Offer offer) {
        String acceptedSession = this.acceptedAwaitingOffer.get(source.presenceId());
        if (!offer.sessionId().equals(acceptedSession)) {
            NliConstants.LOG.warn("[P2P][{}] Ignoring offer for unaccepted session {}; accepted={}", this.accountName, offer.sessionId(), acceptedSession);
            return;
        }
        UUID profileId = this.profileIdFor(source);
        if (profileId == null) {
            NliConstants.LOG.warn(
                "[P2P][{}] Continuing offer from unknown PMID {}; intended profile check will be skipped",
                this.accountName,
                source.presenceId()
            );
        }
        NliConstants.LOG.info("[P2P][{}] Starting answer handshake session={} presence={} profile={}", this.accountName, offer.sessionId(), source.presenceId(), profileId);
        this.startAnswerHandshake(source, profileId, offer.sessionId(), offer.sdp()).exceptionally(error -> {
            NliConstants.LOG.warn("[P2P][{}] Failed to start handshake {}: {}", this.accountName, offer.sessionId(), error.toString());
            return null;
        });
    }

    private CompletableFuture<Void> startAnswerHandshake(LinkPeerRoute peer, @Nullable UUID profileId, String sessionId, String offerSdp) {
        RtcHandshake previous = this.handshakes.remove(peer.presenceId());
        if (previous != null) {
            NliConstants.LOG.info("[P2P][{}] Superseding handshake {} with retry {}", this.accountName, previous.id(), sessionId);
            previous.abort("superseded by retry " + sessionId);
        }
        CompletableFuture<Void> result = new CompletableFuture<>();
        NliConstants.LOG.info("[P2P][{}] Requesting TURN auth for session={}", this.accountName, sessionId);
        this.signaling.requestTurnAuth().thenCompose(turnAuth -> {
            NliConstants.LOG.info("[P2P][{}] TURN auth ready for session={}", this.accountName, sessionId);
            RtcHandshake handshake = this.createHandshake(peer, profileId, sessionId, turnAuth, result);
            if (handshake == null) {
                return CompletableFuture.failedFuture(new IllegalStateException("Failed to create handshake"));
            }
            return handshake.acceptOffer(offerSdp)
                .whenComplete((_, error) -> {
                    if (error == null) {
                        NliConstants.LOG.info("[P2P][{}] Created answer SDP for session={}", this.accountName, sessionId);
                    }
                })
                .thenCompose(answer -> this.signaling.sendClientMessage(peer, SignalingMessage.answer(sessionId, answer)));
        }).whenComplete((_, error) -> {
            if (error != null) {
                result.completeExceptionally(error);
            }
        });
        return result;
    }

    private @Nullable RtcHandshake createHandshake(LinkPeerRoute peer, @Nullable UUID profileId, String sessionId, RTCIceServer turnAuth, CompletableFuture<Void> result) {
        RTCConfiguration config = new RTCConfiguration();
        config.iceServers.add(turnAuth);
        config.portAllocatorConfig.setEnableIpv6(true).setEnableIpv6OnWifi(true);
        RtcHandshake handshake;
        synchronized (this) {
            handshake = new RtcHandshake(
                this.getPeerConnectionFactory(),
                config,
                sessionId,
                false,
                candidate -> this.signaling.sendClientMessage(peer, SignalingMessage.iceCandidate(sessionId, candidate)).exceptionally(_ -> null)
            );
            if (this.handshakes.putIfAbsent(peer.presenceId(), handshake) != null) {
                handshake.abort("duplicate");
                return null;
            }
        }
        this.acceptedAwaitingOffer.remove(peer.presenceId(), sessionId);
        this.applyPendingIceCandidates(peer.presenceId(), sessionId, handshake);
        NliConstants.LOG.info("[P2P][{}] Created WebRTC handshake session={} presence={}", this.accountName, sessionId, peer.presenceId());
        CompletableFuture.delayedExecutor(HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS).execute(() -> {
            if (!result.isDone()) {
                NliConstants.LOG.warn("[P2P][{}] Handshake timed out session={}", this.accountName, sessionId);
                handshake.abort("timeout");
            }
        });
        handshake.future().whenComplete((handshakeResult, error) -> {
            this.handshakes.remove(peer.presenceId(), handshake);
            this.pendingIceCandidates.remove(new PendingIceKey(peer.presenceId(), sessionId));
            if (error != null) {
                NliConstants.LOG.warn("[P2P][{}] Handshake failed session={}: {}", this.accountName, sessionId, error.toString());
                result.completeExceptionally(error);
            } else if (result.complete(null)) {
                NliConstants.LOG.info("[P2P][{}] Handshake completed session={}, accepting guest profile={}", this.accountName, sessionId, profileId);
                this.acceptGuest(handshakeResult, profileId);
            } else {
                RtcChannel.dispose(handshakeResult);
            }
        });
        return handshake;
    }

    private void handleIceCandidate(LinkPeerRoute source, SignalingMessage.WebRtc.IceCandidate ice) {
        RtcHandshake handshake = this.handshakes.get(source.presenceId());
        if (handshake == null || !handshake.id().equals(ice.sessionId())) {
            if (this.shouldBufferIceCandidate(source.presenceId(), ice.sessionId())) {
                PendingIceKey key = new PendingIceKey(source.presenceId(), ice.sessionId());
                this.pendingIceCandidates.compute(key, (_, existing) -> {
                    List<RTCIceCandidate> candidates = existing != null ? existing : new ArrayList<>();
                    candidates.add(ice.toRtcIceCandidate());
                    return candidates;
                });
                NliConstants.LOG.debug("[P2P][{}] Buffered ICE candidate while waiting for handshake session={}", this.accountName, ice.sessionId());
                return;
            }
            NliConstants.LOG.debug("[P2P][{}] Dropping ICE candidate for stale/missing session={}", this.accountName, ice.sessionId());
            return;
        }
        NliConstants.LOG.debug("[P2P][{}] Applying ICE candidate session={}", this.accountName, ice.sessionId());
        RTCIceCandidate candidate = ice.toRtcIceCandidate();
        handshake.addRemoteIceCandidate(candidate).exceptionally(error -> {
            NliConstants.LOG.warn("[P2P][{}] Failed to add ICE candidate for {}: {}", this.accountName, ice.sessionId(), error.getMessage());
            return null;
        });
    }

    private void acceptGuest(RtcHandshake.HandshakeResult handshakeResult, @Nullable UUID profileId) {
        NliConstants.LOG.info("[P2P][{}] Registering RTC channel with server profile={}", this.accountName, profileId);
        this.connectionBridge.accept(new RtcChannel(handshakeResult), profileId);
    }

    private @Nullable UUID profileIdFor(LinkPeerRoute peer) {
        return peer.profileId() != null ? peer.profileId() : this.profileIdsByPresence.get(peer.presenceId());
    }

    private boolean shouldBufferIceCandidate(String presenceId, String sessionId) {
        String acceptedSession = this.acceptedAwaitingOffer.get(presenceId);
        return sessionId.equals(acceptedSession);
    }

    private void applyPendingIceCandidates(String presenceId, String sessionId, RtcHandshake handshake) {
        List<RTCIceCandidate> buffered = this.pendingIceCandidates.remove(new PendingIceKey(presenceId, sessionId));
        if (buffered == null || buffered.isEmpty()) {
            return;
        }
        buffered.forEach(candidate -> handshake.addRemoteIceCandidate(candidate).exceptionally(error -> {
                NliConstants.LOG.warn("[P2P][{}] Failed to replay buffered ICE candidate for {}: {}", this.accountName, sessionId, error.getMessage());
                return null;
            }));
    }

    private PeerConnectionFactory getPeerConnectionFactory() {
        if (this.factory == null) {
            this.factory = new PeerConnectionFactory();
        }
        return this.factory;
    }

    private record PendingIceKey(String presenceId, String sessionId) {
    }
}
