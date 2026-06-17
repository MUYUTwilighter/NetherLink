package cool.muyucloud.netherlink.link;

import cool.muyucloud.netherlink.p2p.SignalingException;
import cool.muyucloud.netherlink.p2p.SignalingMessage;
import dev.onvoid.webrtc.RTCIceServer;
import org.jspecify.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface LinkSignalingClient {
    void setFriendJoinHandler(@Nullable FriendJoinHandler handler);

    void setWebRtcSignalingHandler(@Nullable WebRtcSignalingHandler handler);

    void addConnectionListener(ConnectionListener listener);

    void removeConnectionListener(ConnectionListener listener);

    void connect();

    void disconnect();

    void shutdown();

    CompletableFuture<Void> sendClientMessage(UUID toPlayerId, SignalingMessage message);

    CompletableFuture<RTCIceServer> requestTurnAuth();

    @FunctionalInterface
    interface FriendJoinHandler {
        void handle(UUID fromPresenceId, SignalingMessage.FriendJoin message);
    }

    @FunctionalInterface
    interface WebRtcSignalingHandler {
        void handle(UUID fromPresenceId, SignalingMessage.WebRtc message);
    }

    interface ConnectionListener {
        default void onSignalingError(@Nullable UUID peerPresenceId, SignalingException cause) {
        }

        default void onSignalingConnected() {
        }

        default void onSignalingDisconnected() {
        }

        default void onSignalingConnectFailed() {
        }
    }

    default void fireListeners(Iterable<ConnectionListener> listeners, Consumer<ConnectionListener> action) {
        for (ConnectionListener listener : listeners) {
            action.accept(listener);
        }
    }
}
