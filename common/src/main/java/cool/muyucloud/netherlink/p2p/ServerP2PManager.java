package cool.muyucloud.netherlink.p2p;

import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.account.MinecraftAccount;
import dev.onvoid.webrtc.PeerConnectionFactory;
import dev.onvoid.webrtc.RTCConfiguration;
import dev.onvoid.webrtc.RTCIceCandidate;
import dev.onvoid.webrtc.RTCIceServer;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class ServerP2PManager {
    private static final long SIGNALING_RECONNECT_DELAY_SECONDS = 1L;
    private static final long HANDSHAKE_TIMEOUT_SECONDS = 30L;

    private final String accountName;
    private final MinecraftAccount account;
    private final MinecraftServer server;
    private final SignalingClient signaling;
    private final ConcurrentHashMap<UUID, UUID> profileIdsByPmid = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> acceptedAwaitingOffer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, RtcHandshake> handshakes = new ConcurrentHashMap<>();
    private final SignalingClient.ConnectionListener connectionListener = new SignalingClient.ConnectionListener() {
        @Override
        public void onSignalingError(@Nullable UUID peerPmid, SignalingException cause) {
            if (peerPmid != null) {
                RtcHandshake handshake = ServerP2PManager.this.handshakes.get(peerPmid);
                if (handshake != null) {
                    handshake.abort("signaling error: " + cause.getClass().getSimpleName());
                }
            }
        }

        @Override
        public void onSignalingDisconnected() {
            ServerP2PManager.this.onSignalingDisconnected();
        }
    };
    private @Nullable PeerConnectionFactory factory;
    private volatile boolean shutdown;

    public ServerP2PManager(String accountName, MinecraftAccount account, MinecraftServer server) {
        this.accountName = accountName;
        this.account = account;
        this.server = server;
        this.signaling = new SignalingClient(account.getMcToken(), "NetherLink Signaling-" + accountName);
        this.signaling.setFriendJoinHandler(this::handleFriendJoin);
        this.signaling.setWebRtcSignalingHandler(this::handleWebRtc);
        this.signaling.addConnectionListener(this.connectionListener);
    }

    public void start() {
        this.shutdown = false;
        NliConstants.LOG.info("[P2P][{}] Starting server P2P manager", this.accountName);
        this.signaling.connect();
        this.warmupTurnAuth();
    }

    public void updatePresence(java.util.Map<UUID, UUID> profileIdsByPmid) {
        this.profileIdsByPmid.clear();
        this.profileIdsByPmid.putAll(profileIdsByPmid);
        NliConstants.LOG.info("[P2P][{}] Updated presence peer map: {} entries", this.accountName, profileIdsByPmid.size());
    }

    public synchronized void shutdown() {
        this.shutdown = true;
        NliConstants.LOG.info("[P2P][{}] Shutting down server P2P manager", this.accountName);
        this.signaling.removeConnectionListener(this.connectionListener);
        this.handshakes.values().forEach(handshake -> handshake.abort("shutdown"));
        this.handshakes.clear();
        this.acceptedAwaitingOffer.clear();
        if (this.factory != null) {
            this.factory.dispose();
            this.factory = null;
        }
        this.signaling.shutdown();
    }

    private void onSignalingDisconnected() {
        if (!this.shutdown) {
            NliConstants.LOG.warn("[P2P][{}] Signaling disconnected, reconnecting in {}s", this.accountName, SIGNALING_RECONNECT_DELAY_SECONDS);
            CompletableFuture.delayedExecutor(SIGNALING_RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS).execute(() -> {
                if (!this.shutdown) {
                    this.signaling.connect();
                    this.warmupTurnAuth();
                }
            });
        }
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

    private void handleFriendJoin(UUID fromPmid, SignalingMessage.FriendJoin message) {
        NliConstants.LOG.info("[P2P][{}] Received friend join message {} from {}", this.accountName, message.getClass().getSimpleName(), fromPmid);
        switch (message) {
            case SignalingMessage.FriendJoin.Request request -> this.acceptJoinRequest(fromPmid, request.sessionId());
            case SignalingMessage.FriendJoin.InviteDeclined ignored -> this.acceptedAwaitingOffer.remove(fromPmid);
            case SignalingMessage.FriendJoin.Accepted ignored -> {
            }
            case SignalingMessage.FriendJoin.Rejected ignored -> {
            }
        }
    }

    private void acceptJoinRequest(UUID fromPmid, String sessionId) {
        UUID profileId = this.profileIdForPmid(fromPmid);
        if (profileId == null) {
            NliConstants.LOG.warn(
                "[P2P][{}] Accepting join request from unknown PMID {}; server login authentication will verify the player",
                this.accountName,
                fromPmid
            );
        } else {
            NliConstants.LOG.info("[P2P][{}] Accepting join request session={} pmid={} profile={}", this.accountName, sessionId, fromPmid, profileId);
        }
        this.acceptedAwaitingOffer.put(fromPmid, sessionId);
        this.signaling.sendClientMessage(fromPmid, SignalingMessage.joinAccepted(sessionId)).exceptionally(error -> {
            this.acceptedAwaitingOffer.remove(fromPmid, sessionId);
            NliConstants.LOG.warn("[P2P][{}] Failed to accept join request {}: {}", this.accountName, sessionId, error.getMessage());
            return null;
        });
    }

    private void handleWebRtc(UUID fromPmid, SignalingMessage.WebRtc message) {
        NliConstants.LOG.info("[P2P][{}] Received WebRTC message {} session={} from {}", this.accountName, message.getClass().getSimpleName(), message.sessionId(), fromPmid);
        switch (message) {
            case SignalingMessage.WebRtc.Offer offer -> this.handleOffer(fromPmid, offer);
            case SignalingMessage.WebRtc.IceCandidate ice -> this.handleIceCandidate(fromPmid, ice);
            case SignalingMessage.WebRtc.Answer ignored -> {
            }
        }
    }

    private void handleOffer(UUID fromPmid, SignalingMessage.WebRtc.Offer offer) {
        String acceptedSession = this.acceptedAwaitingOffer.remove(fromPmid);
        if (!offer.sessionId().equals(acceptedSession)) {
            NliConstants.LOG.warn("[P2P][{}] Ignoring offer for unaccepted session {}; accepted={}", this.accountName, offer.sessionId(), acceptedSession);
            return;
        }
        UUID profileId = this.profileIdForPmid(fromPmid);
        if (profileId == null) {
            NliConstants.LOG.warn(
                "[P2P][{}] Continuing offer from unknown PMID {}; intended profile check will be skipped",
                this.accountName,
                fromPmid
            );
        }
        NliConstants.LOG.info("[P2P][{}] Starting answer handshake session={} pmid={} profile={}", this.accountName, offer.sessionId(), fromPmid, profileId);
        this.startAnswerHandshake(fromPmid, profileId, offer.sessionId(), offer.sdp()).exceptionally(error -> {
            NliConstants.LOG.warn("[P2P][{}] Failed to start handshake {}: {}", this.accountName, offer.sessionId(), error.toString());
            return null;
        });
    }

    private CompletableFuture<Void> startAnswerHandshake(UUID peerPmid, @Nullable UUID profileId, String sessionId, String offerSdp) {
        if (this.handshakes.containsKey(peerPmid)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Handshake already in progress"));
        }
        CompletableFuture<Void> result = new CompletableFuture<>();
        NliConstants.LOG.info("[P2P][{}] Requesting TURN auth for session={}", this.accountName, sessionId);
        this.signaling.requestTurnAuth().thenCompose(turnAuth -> {
            NliConstants.LOG.info("[P2P][{}] TURN auth ready for session={}", this.accountName, sessionId);
            RtcHandshake handshake = this.createHandshake(peerPmid, profileId, sessionId, turnAuth, result);
            if (handshake == null) {
                return CompletableFuture.failedFuture(new IllegalStateException("Failed to create handshake"));
            }
            return handshake.acceptOffer(offerSdp)
                .whenComplete((answer, error) -> {
                    if (error == null) {
                        NliConstants.LOG.info("[P2P][{}] Created answer SDP for session={}", this.accountName, sessionId);
                    }
                })
                .thenCompose(answer -> this.signaling.sendClientMessage(peerPmid, SignalingMessage.answer(sessionId, answer)));
        }).whenComplete((ignored, error) -> {
            if (error != null) {
                result.completeExceptionally(error);
            }
        });
        return result;
    }

    private @Nullable RtcHandshake createHandshake(UUID peerPmid, @Nullable UUID profileId, String sessionId, RTCIceServer turnAuth, CompletableFuture<Void> result) {
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
                candidate -> this.signaling.sendClientMessage(peerPmid, SignalingMessage.iceCandidate(sessionId, candidate)).exceptionally(error -> null)
            );
            if (this.handshakes.putIfAbsent(peerPmid, handshake) != null) {
                handshake.abort("duplicate");
                return null;
            }
        }
        NliConstants.LOG.info("[P2P][{}] Created WebRTC handshake session={} pmid={}", this.accountName, sessionId, peerPmid);
        CompletableFuture.delayedExecutor(HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS).execute(() -> {
            if (!result.isDone()) {
                NliConstants.LOG.warn("[P2P][{}] Handshake timed out session={}", this.accountName, sessionId);
                handshake.abort("timeout");
            }
        });
        handshake.future().whenComplete((handshakeResult, error) -> {
            this.handshakes.remove(peerPmid, handshake);
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

    private void handleIceCandidate(UUID fromPmid, SignalingMessage.WebRtc.IceCandidate ice) {
        RtcHandshake handshake = this.handshakes.get(fromPmid);
        if (handshake == null || !handshake.id().equals(ice.sessionId())) {
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
        this.server.execute(() -> {
            NliConstants.LOG.info("[P2P][{}] Registering RTC channel with server profile={}", this.accountName, profileId);
            ServerChannelAcceptor.accept(this.server, new RtcChannel(handshakeResult), profileId);
        });
    }

    private @Nullable UUID profileIdForPmid(UUID pmid) {
        return this.profileIdsByPmid.get(pmid);
    }

    private PeerConnectionFactory getPeerConnectionFactory() {
        if (this.factory == null) {
            this.factory = new PeerConnectionFactory();
        }
        return this.factory;
    }
}
