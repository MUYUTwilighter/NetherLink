package cool.muyucloud.netherlink.link.nli;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.http.JsonHttp;
import cool.muyucloud.netherlink.link.model.LinkFailure;
import cool.muyucloud.netherlink.link.model.LinkFailureCode;
import cool.muyucloud.netherlink.link.model.LinkPeerRoute;
import cool.muyucloud.netherlink.link.service.LinkSignalingClient;
import cool.muyucloud.netherlink.link.transport.SignalingException;
import cool.muyucloud.netherlink.link.transport.SignalingMessage;
import dev.onvoid.webrtc.RTCIceServer;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.*;

final class NliSignalingClient implements LinkSignalingClient, WebSocket.Listener {
    private final NliApiClient api;
    private final NliSession session;
    private final ScheduledExecutorService executor;
    private final CopyOnWriteArrayList<ConnectionListener> listeners = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, LinkPeerRoute> pendingRoutes = new ConcurrentHashMap<>();
    private final Runnable tokenListener = this::onTokenRotated;
    private final StringBuilder text = new StringBuilder();
    private volatile @Nullable FriendJoinHandler friendJoinHandler;
    private volatile @Nullable WebRtcSignalingHandler webRtcSignalingHandler;
    private volatile @Nullable CompletableFuture<WebSocket> connection;
    private volatile @Nullable WebSocket webSocket;
    private volatile boolean shutdown;

    NliSignalingClient(NliApiClient api, NliSession session, String threadName) {
        this.api = api;
        this.session = session;
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        });
        this.session.addTokenListener(this.tokenListener);
    }

    @Override
    public void setFriendJoinHandler(@Nullable FriendJoinHandler handler) { this.friendJoinHandler = handler; }
    @Override
    public void setWebRtcSignalingHandler(@Nullable WebRtcSignalingHandler handler) { this.webRtcSignalingHandler = handler; }
    @Override
    public void addConnectionListener(ConnectionListener listener) { this.listeners.add(listener); }
    @Override
    public void removeConnectionListener(ConnectionListener listener) { this.listeners.remove(listener); }

    @Override
    public synchronized void connect() {
        if (this.shutdown || this.connection != null) return;
        CompletableFuture<WebSocket> pending = this.api.http().newWebSocketBuilder()
            .header("Authorization", "Bearer " + this.session.token())
            .buildAsync(this.api.websocketUri(), this);
        this.connection = pending;
        pending.whenComplete((socket, error) -> {
            synchronized (this) {
                if (this.connection != pending) {
                    if (socket != null) socket.sendClose(WebSocket.NORMAL_CLOSURE, "superseded");
                    return;
                }
                if (error != null) {
                    this.connection = null;
                    this.webSocket = null;
                } else {
                    this.webSocket = socket;
                }
            }
            if (error != null) {
                this.execute(() -> this.listeners.forEach(ConnectionListener::onSignalingConnectFailed));
            }
        });
    }

    @Override
    public synchronized void disconnect() {
        WebSocket socket = this.webSocket;
        this.webSocket = null;
        this.connection = null;
        if (socket != null) socket.sendClose(WebSocket.NORMAL_CLOSURE, "disconnect");
    }

    @Override
    public synchronized void shutdown() {
        if (this.shutdown) return;
        this.shutdown = true;
        this.session.removeTokenListener(this.tokenListener);
        this.disconnect();
        this.executor.shutdown();
    }

    @Override
    public CompletableFuture<Void> sendClientMessage(LinkPeerRoute target, SignalingMessage message) {
        UUID profileId = target.profileId();
        if (profileId == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("NLI signaling target requires a profile id"));
        }
        CompletableFuture<WebSocket> pending;
        synchronized (this) {
            pending = this.connection;
        }
        if (pending == null) {
            return CompletableFuture.failedFuture(new IOException("NLI signaling is not connected"));
        }
        JsonObject envelope = new JsonObject();
        String messageId = UUID.randomUUID().toString();
        envelope.addProperty("id", messageId);
        envelope.addProperty("type", message.type().name());
        envelope.addProperty("to", profileId.toString());
        envelope.addProperty("toPresenceId", target.presenceId());
        envelope.addProperty("sessionId", message.sessionId());
        envelope.add("payload", SignalingMessage.CODEC.encodeStart(JsonOps.INSTANCE, message)
            .getOrThrow(error -> new IllegalStateException("Cannot encode signaling message: " + error)));
        return pending.thenCompose(socket -> {
            synchronized (this) {
                if (this.connection != pending || this.webSocket != socket) {
                    return CompletableFuture.failedFuture(new IOException("NLI signaling disconnected before sending"));
                }
            }
            this.pendingRoutes.put(messageId, target);
            this.executor.schedule(() -> this.pendingRoutes.remove(messageId), 30L, TimeUnit.SECONDS);
            return socket.sendText(envelope.toString(), true);
        }).handle((ignored1, error) -> {
            if (error != null) {
                this.pendingRoutes.remove(messageId);
                throw new CompletionException(error);
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<RTCIceServer> requestTurnAuth() {
        return this.api.post("v1/turn", this.session.token(), null).thenApply(json -> {
            RTCIceServer server = new RTCIceServer();
            server.username = JsonHttp.requiredString(json, "username");
            server.password = JsonHttp.requiredString(json, "credential");
            JsonArray urls = JsonHttp.array(json, "urls");
            for (JsonElement url : urls) server.urls.add(url.getAsString());
            if (server.urls.isEmpty()) throw new IllegalStateException("NLI TURN response contains no URLs");
            return server;
        }).exceptionallyCompose(error -> CompletableFuture.failedFuture(
            new SignalingException.TurnAuthFailedException(error.getMessage())
        ));
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        if (this.shutdown) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
            return;
        }
        this.webSocket = webSocket;
        webSocket.request(1L);
        this.execute(() -> this.listeners.forEach(ConnectionListener::onSignalingConnected));
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        synchronized (this.text) {
            this.text.append(data);
            if (last) {
                String frame = this.text.toString();
                this.text.setLength(0);
                this.execute(() -> this.handleFrame(frame));
            }
        }
        webSocket.request(1L);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
        webSocket.request(1L);
        return webSocket.sendPong(message);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        boolean current;
        synchronized (this) {
            current = this.webSocket == webSocket;
            if (current) {
                this.webSocket = null;
                this.connection = null;
            }
        }
        if (current && !this.shutdown) {
            this.pendingRoutes.clear();
            this.execute(() -> this.listeners.forEach(ConnectionListener::onSignalingDisconnected));
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        NliConstants.LOG.warn("NLI signaling connection failed for {}", this.session.runtimeKey(), error);
        this.onClose(webSocket, 1006, error.getMessage());
    }

    private void handleFrame(String frame) {
        try {
            JsonObject envelope = JsonHttp.parseObject(frame);
            String type = JsonHttp.requiredString(envelope, "type");
            if ("ERROR".equals(type)) {
                this.handleError(envelope);
                return;
            }
            LinkPeerRoute source = new LinkPeerRoute(
                JsonHttp.requiredUuid(envelope, "from"),
                JsonHttp.requiredString(envelope, "fromPresenceId")
            );
            JsonElement payload = envelope.get("payload");
            SignalingMessage message = SignalingMessage.CODEC.parse(JsonOps.INSTANCE, payload)
                .getOrThrow(error -> new IllegalStateException("Malformed NLI signaling payload: " + error));
            if (message instanceof SignalingMessage.FriendJoin friendJoin) {
                FriendJoinHandler handler = this.friendJoinHandler;
                if (handler != null) handler.handle(source, friendJoin);
            } else if (message instanceof SignalingMessage.WebRtc webRtc) {
                WebRtcSignalingHandler handler = this.webRtcSignalingHandler;
                if (handler != null) handler.handle(source, webRtc);
            }
        } catch (RuntimeException error) {
            NliConstants.LOG.warn("Dropping malformed NLI signaling frame", error);
        }
    }

    private void handleError(JsonObject envelope) {
        String code = JsonHttp.string(envelope, "code", "UNKNOWN");
        String message = JsonHttp.string(envelope, "message", "NLI signaling rejected the request");
        SignalingException error = new NliSignalException(failureCode(code), message);
        String id = JsonHttp.string(envelope, "id");
        LinkPeerRoute peer = id != null ? this.pendingRoutes.remove(id) : null;
        this.listeners.forEach(listener -> listener.onSignalingError(peer, error));
        if ("INVALID_INSTANCE_TOKEN".equals(code)) this.disconnect();
    }

    private void onTokenRotated() {
        this.execute(() -> {
            boolean reconnect = this.connection != null;
            this.disconnect();
            if (reconnect && !this.shutdown) this.connect();
        });
    }

    private void execute(Runnable action) {
        try {
            this.executor.execute(action);
        } catch (RejectedExecutionException ignored) {
            // The client was shut down concurrently with this callback.
        }
    }

    private static LinkFailureCode failureCode(String code) {
        return switch (code) {
            case "INVALID_INSTANCE_TOKEN", "UNAUTHORIZED" -> LinkFailureCode.UNAUTHORIZED;
            case "TARGET_UNAVAILABLE" -> LinkFailureCode.TARGET_UNAVAILABLE;
            case "TARGET_NOT_JOINABLE" -> LinkFailureCode.TARGET_NOT_JOINABLE;
            case "NOT_FRIENDS" -> LinkFailureCode.NOT_FRIENDS;
            case "SESSION_NOT_FOUND", "INVALID_SESSION_STATE" -> LinkFailureCode.INVALID_SESSION;
            case "RATE_LIMITED" -> LinkFailureCode.RATE_LIMITED;
            case "CONNECTION_LIMIT" -> LinkFailureCode.CONNECTION_LIMIT;
            case "FORBIDDEN" -> LinkFailureCode.FORBIDDEN;
            default -> LinkFailureCode.UNKNOWN;
        };
    }

    private static final class NliSignalException extends SignalingException {
        private NliSignalException(LinkFailureCode code, String message) {
            super(null, new LinkFailure(code, message, code == LinkFailureCode.RATE_LIMITED || code == LinkFailureCode.TARGET_UNAVAILABLE));
        }
    }
}
