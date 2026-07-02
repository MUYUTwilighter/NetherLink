package cool.muyucloud.netherlink.link.service;

import cool.muyucloud.netherlink.link.model.LinkPeerRoute;
import cool.muyucloud.netherlink.link.transport.SignalingException;
import cool.muyucloud.netherlink.link.transport.SignalingMessage;
import dev.onvoid.webrtc.RTCIceServer;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Backend-internal signaling adapter consumed by the shared WebRTC transport.
 *
 * <p>One instance authenticates exactly one source Presence. It must not multiplex several
 * runtime tokens. Implementations must redact credentials, SDP, and ICE data from logs.</p>
 */
public interface LinkSignalingClient {
    /** Installs or clears the inbound join-control handler. */
    void setFriendJoinHandler(@Nullable FriendJoinHandler handler);

    /** Installs or clears the inbound SDP/ICE handler. */
    void setWebRtcSignalingHandler(@Nullable WebRtcSignalingHandler handler);

    /** Registers an identity-based connection listener. */
    void addConnectionListener(ConnectionListener listener);

    /** Removes a previously registered listener. */
    void removeConnectionListener(ConnectionListener listener);

    /** Starts or reuses an asynchronous signaling connection. */
    void connect();

    /** Disconnects without permanently disposing this adapter. */
    void disconnect();

    /** Permanently closes the adapter and pending requests. */
    void shutdown();

    /** Sends one backend-neutral signaling message to a specific public Presence route. */
    CompletableFuture<Void> sendClientMessage(LinkPeerRoute target, SignalingMessage message);

    /** Fetches short-lived ICE server credentials for a new WebRTC negotiation. */
    CompletableFuture<RTCIceServer> requestTurnAuth();

    @FunctionalInterface
    interface FriendJoinHandler {
        void handle(LinkPeerRoute source, SignalingMessage.FriendJoin message);
    }

    @FunctionalInterface
    interface WebRtcSignalingHandler {
        void handle(LinkPeerRoute source, SignalingMessage.WebRtc message);
    }

    /** Connection lifecycle callbacks; implementations may invoke them on an I/O thread. */
    interface ConnectionListener {
        default void onSignalingError(@Nullable LinkPeerRoute peer, SignalingException cause) {
        }

        default void onSignalingConnected() {
        }

        default void onSignalingDisconnected() {
        }

        default void onSignalingConnectFailed() {
        }
    }

}
