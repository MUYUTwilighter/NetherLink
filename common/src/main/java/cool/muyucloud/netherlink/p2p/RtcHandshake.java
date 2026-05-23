package cool.muyucloud.netherlink.p2p;

import cool.muyucloud.netherlink.NliConstants;
import dev.onvoid.webrtc.*;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class RtcHandshake {
    private final String id;
    private final RTCPeerConnection peerConnection;
    private final boolean initiator;
    private final Consumer<RTCIceCandidate> onLocalCandidate;
    private final CompletableFuture<HandshakeResult> result = new CompletableFuture<>();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean handedOff = new AtomicBoolean();
    private volatile @Nullable CompletableFuture<String> sdpResult;
    private volatile @Nullable RTCDataChannel dataChannel;

    public RtcHandshake(PeerConnectionFactory factory, RTCConfiguration configuration, String id, boolean initiator, Consumer<RTCIceCandidate> onLocalCandidate) {
        this.id = id;
        this.initiator = initiator;
        this.onLocalCandidate = onLocalCandidate;
        this.peerConnection = factory.createPeerConnection(configuration, new SessionObserver());
    }

    public String id() {
        return id;
    }

    public boolean isInitiator() {
        return initiator;
    }

    public CompletableFuture<HandshakeResult> future() {
        return result;
    }

    public void abort(String reason) {
        NliConstants.LOG.info("[P2P][{}] Aborting handshake: {}", this.id, reason);
        this.failHandshake(reason);
    }

    public CompletableFuture<String> acceptOffer(String offerSdp) {
        NliConstants.LOG.info("[P2P][{}] Accepting remote offer", this.id);
        return !this.started.compareAndSet(false, true)
            ? CompletableFuture.failedFuture(new IllegalStateException("Cannot accept offer after handshake has started"))
            : this.startSdpExchange(
                this.setRemoteDescription(new RTCSessionDescription(RTCSdpType.OFFER, offerSdp))
                    .thenCompose(ignored -> this.createAnswerSdp())
                    .thenCompose(this::setLocalDescription)
            );
    }

    public CompletableFuture<String> createOffer() {
        NliConstants.LOG.info("[P2P][{}] Creating local offer", this.id);
        if (!this.initiator) {
            return CompletableFuture.failedFuture(new IllegalStateException("Only initiators can create offers"));
        }
        if (!this.started.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Cannot create offer after handshake has started"));
        }
        RTCDataChannelInit init = new RTCDataChannelInit();
        this.wireDataChannel(this.peerConnection.createDataChannel("minecraft", init));
        return this.startSdpExchange(
            this.createOfferSdp()
                .thenCompose(this::setLocalDescription)
        );
    }

    public CompletableFuture<Void> applyAnswer(String answerSdp) {
        if (this.result.isDone()) {
            return CompletableFuture.completedFuture(null);
        }
        RTCSignalingState state = this.peerConnection.getSignalingState();
        if (state == RTCSignalingState.STABLE) {
            return CompletableFuture.completedFuture(null);
        }
        return this.setRemoteDescription(new RTCSessionDescription(RTCSdpType.ANSWER, answerSdp));
    }

    public CompletableFuture<Void> addRemoteIceCandidate(RTCIceCandidate candidate) {
        if (this.result.isDone()) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            NliConstants.LOG.debug("[P2P][{}] Adding remote ICE candidate mid={} line={}", this.id, candidate.sdpMid, candidate.sdpMLineIndex);
            this.peerConnection.addIceCandidate(candidate);
            return CompletableFuture.completedFuture(null);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private CompletableFuture<String> startSdpExchange(CompletableFuture<Void> pipeline) {
        CompletableFuture<String> sdpFuture = new CompletableFuture<>();
        this.sdpResult = sdpFuture;
        pipeline.whenComplete((ignored, error) -> {
            if (error != null) {
                NliConstants.LOG.warn("[P2P][{}] SDP exchange failed: {}", this.id, error.toString());
                sdpFuture.completeExceptionally(error);
            } else {
                NliConstants.LOG.info("[P2P][{}] SDP local description is ready", this.id);
                this.completeSdp(sdpFuture);
            }
        });
        return sdpFuture.whenComplete((sdp, error) -> this.sdpResult = null);
    }

    private CompletableFuture<Void> setRemoteDescription(RTCSessionDescription description) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        this.peerConnection.setRemoteDescription(description, new SetSessionDescriptionObserver() {
            @Override
            public void onSuccess() {
                future.complete(null);
            }

            @Override
            public void onFailure(String error) {
                future.completeExceptionally(new RuntimeException("setRemoteDescription: " + error));
            }
        });
        return future;
    }

    private CompletableFuture<Void> setLocalDescription(RTCSessionDescription description) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        this.peerConnection.setLocalDescription(description, new SetSessionDescriptionObserver() {
            @Override
            public void onSuccess() {
                future.complete(null);
            }

            @Override
            public void onFailure(String error) {
                future.completeExceptionally(new RuntimeException("setLocalDescription: " + error));
            }
        });
        return future;
    }

    private CompletableFuture<RTCSessionDescription> createAnswerSdp() {
        CompletableFuture<RTCSessionDescription> future = new CompletableFuture<>();
        this.peerConnection.createAnswer(new RTCAnswerOptions(), new CreateSessionDescriptionObserver() {
            @Override
            public void onSuccess(RTCSessionDescription description) {
                future.complete(description);
            }

            @Override
            public void onFailure(String error) {
                future.completeExceptionally(new RuntimeException("createAnswer: " + error));
            }
        });
        return future;
    }

    private CompletableFuture<RTCSessionDescription> createOfferSdp() {
        CompletableFuture<RTCSessionDescription> future = new CompletableFuture<>();
        this.peerConnection.createOffer(new RTCOfferOptions(), new CreateSessionDescriptionObserver() {
            @Override
            public void onSuccess(RTCSessionDescription description) {
                future.complete(description);
            }

            @Override
            public void onFailure(String error) {
                future.completeExceptionally(new RuntimeException("createOffer: " + error));
            }
        });
        return future;
    }

    private void completeSdp(CompletableFuture<String> sdp) {
        RTCSessionDescription local = this.peerConnection.getLocalDescription();
        if (local == null) {
            sdp.completeExceptionally(new IllegalStateException("local description missing after setLocalDescription"));
        } else {
            sdp.complete(local.sdp);
        }
    }

    private void wireDataChannel(RTCDataChannel channel) {
        this.dataChannel = channel;
        NliConstants.LOG.info("[P2P][{}] DataChannel wired, current state={}", this.id, channel.getState());
        channel.registerObserver(new RTCDataChannelObserver() {
            @Override
            public void onStateChange() {
                RTCDataChannelState state = channel.getState();
                NliConstants.LOG.info("[P2P][{}] DataChannel state -> {}", RtcHandshake.this.id, state);
                if (state == RTCDataChannelState.OPEN) {
                    RtcHandshake.this.markOpen(channel);
                } else if (state == RTCDataChannelState.CLOSING || state == RTCDataChannelState.CLOSED) {
                    RtcHandshake.this.failHandshake("Data channel " + state);
                }
            }

            @Override
            public void onMessage(RTCDataChannelBuffer buffer) {
            }

            @Override
            public void onBufferedAmountChange(long previousAmount) {
            }
        });
        if (channel.getState() == RTCDataChannelState.OPEN) {
            this.markOpen(channel);
        }
    }

    private void markOpen(RTCDataChannel channel) {
        if (!this.result.isDone()) {
            this.handedOff.set(true);
            if (!this.result.complete(new HandshakeResult(this.peerConnection, channel))) {
                this.handedOff.set(false);
            } else {
                channel.unregisterObserver();
                NliConstants.LOG.info("[P2P][{}] handshake complete", this.id);
            }
        }
    }

    private void failHandshake(String reason) {
        Throwable failure = new CancellationException("Handshake " + this.id + " aborted: " + reason);
        if (this.result.completeExceptionally(failure)) {
            NliConstants.LOG.warn("[P2P][{}] Handshake failed: {}", this.id, reason);
            CompletableFuture<String> pending = this.sdpResult;
            if (pending != null) {
                pending.completeExceptionally(failure);
            }
            if (!this.handedOff.get()) {
                RtcChannel.dispose(this.peerConnection, this.dataChannel);
            }
        }
    }

    public record HandshakeResult(RTCPeerConnection peerConnection, RTCDataChannel dataChannel) {
    }

    private final class SessionObserver implements PeerConnectionObserver {
        @Override
        public void onIceCandidate(RTCIceCandidate candidate) {
            if (!RtcHandshake.this.result.isDone()) {
                NliConstants.LOG.debug("[P2P][{}] Local ICE candidate gathered mid={} line={}", RtcHandshake.this.id, candidate.sdpMid, candidate.sdpMLineIndex);
                RtcHandshake.this.onLocalCandidate.accept(candidate);
            }
        }

        @Override
        public void onConnectionChange(RTCPeerConnectionState state) {
            NliConstants.LOG.info("[P2P][{}] PeerConnection state -> {}", RtcHandshake.this.id, state);
            if (state == RTCPeerConnectionState.FAILED || state == RTCPeerConnectionState.CLOSED) {
                RtcHandshake.this.failHandshake("connection " + state);
            }
        }

        @Override
        public void onDataChannel(RTCDataChannel channel) {
            NliConstants.LOG.info("[P2P][{}] DataChannel received from peer", RtcHandshake.this.id);
            RtcHandshake.this.wireDataChannel(channel);
        }

        @Override
        public void onSignalingChange(RTCSignalingState state) {
            NliConstants.LOG.debug("[P2P][{}] Signaling state -> {}", RtcHandshake.this.id, state);
        }

        @Override
        public void onIceConnectionChange(RTCIceConnectionState state) {
            NliConstants.LOG.info("[P2P][{}] ICE connection state -> {}", RtcHandshake.this.id, state);
        }

        @Override
        public void onIceGatheringChange(RTCIceGatheringState state) {
            NliConstants.LOG.debug("[P2P][{}] ICE gathering state -> {}", RtcHandshake.this.id, state);
        }

        @Override
        public void onIceCandidateError(RTCPeerConnectionIceErrorEvent event) {
            NliConstants.LOG.warn("[P2P][{}] ICE error: url={} code={} text={}", RtcHandshake.this.id, event.getUrl(), event.getErrorCode(), event.getErrorText());
        }
    }
}
