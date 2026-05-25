package cool.muyucloud.netherlink.p2p;

import com.google.gson.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import cool.muyucloud.netherlink.ExternalNetwork;
import cool.muyucloud.netherlink.NliConstants;
import dev.onvoid.webrtc.RTCIceServer;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.jsonrpc.JsonRPCErrors;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class SignalingClient {
    private static final Codec<String> SIGNALING_URI_CODEC = Codec.STRING.fieldOf("signalingUri").codec().fieldOf("result").codec();
    private static final Duration PING_INTERVAL = Duration.ofSeconds(50L);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30L);
    private static final String HEADER_AUTH = "x-mojangauth";
    private static final String HEADER_SESSION_ID = "Session-Id";
    private static final String HEADER_REQUEST_ID = "Request-Id";
    private static final Environment ENVIRONMENT = Optional.ofNullable(System.getenv("signaling.environment"))
        .or(() -> Optional.ofNullable(System.getProperty("signaling.environment")))
        .flatMap(Environment::byName)
        .orElse(Environment.PRODUCTION);

    private final String accessToken;
    private final String sessionId = UUID.randomUUID().toString();
    private final ScheduledExecutorService executor;
    private final ExecutorService httpExecutor;
    private final List<ConnectionListener> connectionListeners = new CopyOnWriteArrayList<>();
    private @Nullable HttpClient httpClient;
    private @Nullable CompletableFuture<JsonRpcClient> websocketConnect;
    private @Nullable ScheduledFuture<?> pingTask;
    private @Nullable FriendJoinHandler friendJoinHandler;
    private @Nullable WebRtcSignalingHandler webRtcSignalingHandler;
    private @Nullable CachedTurn cachedTurn;
    private @Nullable CachedSignalingUri cachedSignalingUri;
    private @Nullable CompletableFuture<RTCIceServer> pendingTurnRefresh;

    public SignalingClient(String accessToken, String threadName) {
        this.accessToken = accessToken;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, threadName);
            thread.setDaemon(true);
            return thread;
        });
        AtomicInteger httpThreadId = new AtomicInteger();
        this.httpExecutor = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, threadName + " HTTP #" + httpThreadId.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    public void setFriendJoinHandler(@Nullable FriendJoinHandler handler) {
        this.executor.execute(() -> this.friendJoinHandler = handler);
    }

    public void setWebRtcSignalingHandler(@Nullable WebRtcSignalingHandler handler) {
        this.executor.execute(() -> this.webRtcSignalingHandler = handler);
    }

    public void addConnectionListener(ConnectionListener listener) {
        this.connectionListeners.add(listener);
    }

    public void removeConnectionListener(ConnectionListener listener) {
        this.connectionListeners.remove(listener);
    }

    public void connect() {
        NliConstants.LOG.info("[P2P][signaling] Connecting signaling session {}", this.sessionId);
        this.executor.execute(this::connectWebSocket);
    }

    public void disconnect() {
        NliConstants.LOG.info("[P2P][signaling] Disconnect requested for session {}", this.sessionId);
        this.executor.execute(() -> this.teardown("explicit disconnect"));
    }

    public void shutdown() {
        NliConstants.LOG.info("[P2P][signaling] Shutdown requested for session {}", this.sessionId);
        this.executor.execute(() -> {
            this.friendJoinHandler = null;
            this.webRtcSignalingHandler = null;
            this.teardown("shutdown");
            this.httpExecutor.shutdownNow();
            this.executor.shutdown();
        });
    }

    public CompletableFuture<Void> sendClientMessage(UUID toPlayerId, SignalingMessage message) {
        NliConstants.LOG.info("[P2P][signaling] Sending {} session={} to {}", message.type(), message.sessionId(), toPlayerId);
        String encoded = SignalingMessage.CODEC.encodeStart(JsonOps.INSTANCE, message).getOrThrow(IllegalStateException::new).toString();
        return CompletableFuture.completedFuture(null)
            .thenComposeAsync(ignored -> this.sendRequest("Signaling_SendClientMessage_v1_0", List.of(
                JsonNull.INSTANCE,
                new JsonPrimitive(toPlayerId.toString()),
                new JsonPrimitive(encoded)
            )), this.executor)
            .thenApply(ignored -> (Void)null)
            .exceptionallyCompose(error -> {
                if (error.getCause() instanceof JsonRpcException rpcError) {
                    SignalingException mapped = SignalingErrorMapper.fromJsonRpc(toPlayerId, rpcError);
                    this.fireListeners(listener -> listener.onSignalingError(toPlayerId, mapped));
                    return CompletableFuture.<Void>failedFuture(mapped);
                }
                return CompletableFuture.<Void>failedFuture(error);
            });
    }

    public CompletableFuture<RTCIceServer> requestTurnAuth() {
        return CompletableFuture.completedFuture(null).thenComposeAsync(ignored -> {
            CachedTurn cached = this.cachedTurn;
            if (cached != null && cached.isUsable()) {
                NliConstants.LOG.info("[P2P][signaling] Using cached TURN auth");
                return CompletableFuture.completedFuture(cached.turnAuth().toRtcIceServer());
            }
            if (this.pendingTurnRefresh != null) {
                return this.pendingTurnRefresh;
            }
            CompletableFuture<RTCIceServer> refresh = this.refreshTurnAuth();
            this.pendingTurnRefresh = refresh;
            refresh.whenCompleteAsync((turn, error) -> this.pendingTurnRefresh = null, this.executor);
            return refresh;
        }, this.executor);
    }

    private CompletableFuture<JsonElement> sendRequest(String method, List<JsonElement> params) {
        return this.websocketConnect == null
            ? CompletableFuture.failedFuture(new IllegalStateException("Signaling is not connected; call connect() first"))
            : this.websocketConnect.thenCompose(rpc -> rpc.sendRequest(method, params, REQUEST_TIMEOUT));
    }

    private CompletableFuture<RTCIceServer> refreshTurnAuth() {
        return this.refreshTurnAuth(false);
    }

    private CompletableFuture<RTCIceServer> refreshTurnAuth(boolean retry) {
        NliConstants.LOG.info("[P2P][signaling] Requesting TURN auth");
        return this.refreshTurnAuthResponse(retry)
            .exceptionallyCompose(error -> CompletableFuture.failedFuture(
                this.mapTurnAuthError(error)
            ))
            .thenApplyAsync(result -> {
                NliConstants.LOG.info("[P2P][signaling] TURN auth response received");
                TurnAuthResult turnAuth = TurnAuthResult.CODEC.parse(JsonOps.INSTANCE, result)
                    .getOrThrow(message -> new IllegalStateException("Malformed TurnAuth response: " + message));
                this.cachedTurn = new CachedTurn(turnAuth);
                NliConstants.LOG.info("[P2P][signaling] Received TURN auth with {} server entries", turnAuth.turnAuthServers().size());
                return turnAuth.toRtcIceServer();
            }, this.executor);
    }

    private CompletableFuture<JsonElement> refreshTurnAuthResponse(boolean retry) {
        ExternalNetwork.logCurrentPrefs("Requesting TURN auth");
        logNetworkPreferences("Requesting TURN auth");
        return this.sendRequest("Signaling_TurnAuth_v1_0", List.of())
            .whenComplete((ignored, error) -> {
                if (error != null) {
                    NliConstants.LOG.warn("[P2P][signaling] TURN auth request failed: {}", error.toString());
                }
            })
            .exceptionallyComposeAsync(error -> {
                if (!retry && isTimeout(error)) {
                    NliConstants.LOG.warn("[P2P][signaling] TURN auth timed out; reconnecting signaling session and retrying once");
                    this.cachedSignalingUri = null;
                    this.teardown("TURN auth timeout");
                    this.connectWebSocket();
                    return this.refreshTurnAuthResponse(true);
                }
                return CompletableFuture.failedFuture(error);
            }, this.executor);
    }

    private static boolean isTimeout(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof TimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Throwable mapTurnAuthError(Throwable error) {
        if (error.getCause() instanceof JsonRpcException rpcError) {
            SignalingException mapped = SignalingErrorMapper.fromJsonRpc(null, rpcError);
            if (mapped instanceof SignalingException.SignalingAuthException) {
                this.fireListeners(listener -> listener.onSignalingError(null, mapped));
                return mapped;
            }
            return new SignalingException.TurnAuthFailedException(mapped.getMessage());
        }
        return error;
    }

    private void connectWebSocket() {
        if (this.websocketConnect != null) {
            NliConstants.LOG.debug("[P2P][signaling] Connect skipped; websocket already pending/connected");
            return;
        }
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15L))
            .executor(this.httpExecutor)
            .build();
        this.httpClient = client;
        JsonRpcClient rpc = new JsonRpcClient(this.executor, this::onRpcMethod, this::onWebsocketDown);
        String requestId = UUID.randomUUID().toString();
        this.websocketConnect = this.getSignalingUri(client, requestId)
            .thenComposeAsync(wsUrl -> this.openWebSocket(client, rpc, wsUrl, requestId), this.executor);
        this.websocketConnect.whenCompleteAsync((ignored, error) -> {
            if (error != null) {
                Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                NliConstants.LOG.warn("Signaling websocket connect failed: {}", cause.toString());
                if (cause instanceof SignalingException.SignalingAuthException authError) {
                    this.fireListeners(listener -> listener.onSignalingError(null, authError));
                }
                this.cachedSignalingUri = null;
                if (this.teardown("websocket connect failed: " + cause.getMessage())) {
                    this.fireListeners(ConnectionListener::onSignalingConnectFailed);
                }
            } else {
                NliConstants.LOG.info("[P2P][signaling] WebSocket connected for session {}", this.sessionId);
            }
        }, this.executor);
    }

    private CompletableFuture<String> getSignalingUri(HttpClient client, String requestId) {
        CachedSignalingUri cached = this.cachedSignalingUri;
        if (cached != null && cached.isUsable()) {
            NliConstants.LOG.debug("[P2P][signaling] Using cached signaling URI");
            return CompletableFuture.completedFuture(cached.wsUrl());
        }
        ExternalNetwork.logCurrentPrefs("Fetching signaling configuration");
        logNetworkPreferences("Fetching signaling configuration");
        NliConstants.LOG.info("[P2P][signaling] Fetching signaling configuration from {}", ENVIRONMENT.getConfigurationUri());
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(ENVIRONMENT.getConfigurationUri()))
            .header(HEADER_AUTH, this.accessToken)
            .header(HEADER_SESSION_ID, this.sessionId)
            .header(HEADER_REQUEST_ID, requestId)
            .GET()
            .build();
        return client.sendAsync(request, BodyHandlers.ofString())
            .thenApplyAsync(response -> {
                if (response.statusCode() == 401) {
                    throw new SignalingException.SignalingAuthException("Signaling configuration failed: HTTP 401");
                }
                if (response.statusCode() != 200) {
                    throw new IllegalStateException("Unexpected config response status: " + response.statusCode());
                }
                String baseUri = SIGNALING_URI_CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(response.body()))
                    .getOrThrow(message -> new IllegalStateException("Malformed config response: " + message));
                String wsUrl = baseUri + "/ws/v1.0/messaging/connect/java";
                this.cachedSignalingUri = new CachedSignalingUri(wsUrl);
                NliConstants.LOG.info("[P2P][signaling] Signaling URI acquired");
                return wsUrl;
            }, this.executor);
    }

    private CompletableFuture<JsonRpcClient> openWebSocket(HttpClient client, JsonRpcClient rpc, String wsUrl, String requestId) {
        ExternalNetwork.logCurrentPrefs("Opening signaling websocket");
        return client.newWebSocketBuilder()
            .header(HEADER_AUTH, this.accessToken)
            .header(HEADER_SESSION_ID, this.sessionId)
            .header(HEADER_REQUEST_ID, requestId)
            .buildAsync(URI.create(wsUrl), rpc)
            .thenApplyAsync(webSocket -> {
                this.schedulePing(rpc);
                this.fireListeners(ConnectionListener::onSignalingConnected);
                return rpc;
            }, this.executor);
    }

    private void schedulePing(JsonRpcClient rpc) {
        this.pingTask = this.executor.scheduleAtFixedRate(() -> rpc.sendNotification("System_Ping_v1_0"), PING_INTERVAL.toMillis(), PING_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void onWebsocketDown() {
        NliConstants.LOG.warn("[P2P][signaling] WebSocket closed for session {}", this.sessionId);
        if (this.teardown("websocket closed")) {
            this.fireListeners(ConnectionListener::onSignalingDisconnected);
        }
    }

    private boolean teardown(String reason) {
        HttpClient client = this.httpClient;
        CompletableFuture<JsonRpcClient> connectFuture = this.websocketConnect;
        if (client == null || connectFuture == null) {
            return false;
        }
        NliConstants.LOG.info("[P2P][signaling] Tearing down session {}: {}", this.sessionId, reason);
        if (this.pingTask != null) {
            this.pingTask.cancel(false);
            this.pingTask = null;
        }
        this.pendingTurnRefresh = null;
        connectFuture.whenComplete((rpc, error) -> {
            CompletableFuture<?> closed = rpc != null ? rpc.close() : CompletableFuture.completedFuture(null);
            closed.whenComplete((ignored, closeError) -> {
                try {
                    client.close();
                } catch (RuntimeException closeError2) {
                    NliConstants.LOG.debug("[P2P][signaling] HttpClient close failed", closeError2);
                }
            });
        });
        connectFuture.completeExceptionally(new IllegalStateException("Signaling torn down: " + reason));
        this.httpClient = null;
        this.websocketConnect = null;
        return true;
    }

    private void onRpcMethod(JsonRpcClient rpc, @Nullable JsonElement id, String method, @Nullable JsonElement params) {
        switch (method) {
            case "System_Pong_v1_0" -> {
            }
            case "Signaling_ReceiveMessage_v1_0" -> {
                NliConstants.LOG.debug("[P2P][signaling] Received Signaling_ReceiveMessage_v1_0");
                this.handleReceiveMessage(rpc, id, params);
            }
            default -> {
                NliConstants.LOG.debug("[P2P][signaling] Unknown RPC method {}", method);
                if (id != null) {
                    rpc.sendError(id, JsonRPCErrors.METHOD_NOT_FOUND, method);
                }
            }
        }
    }

    private void handleReceiveMessage(JsonRpcClient rpc, @Nullable JsonElement id, @Nullable JsonElement params) {
        JsonArray array = params != null && params.isJsonArray() ? params.getAsJsonArray() : null;
        JsonElement first = array != null && !array.isEmpty() ? array.get(0) : null;
        if (first == null || !first.isJsonObject()) {
            return;
        }
        if (id != null) {
            rpc.sendResponse(id, new JsonObject());
        }
        ClientWebRtcMessage envelope = ClientWebRtcMessage.CODEC.parse(JsonOps.INSTANCE, first)
            .getOrThrow(error -> new IllegalStateException("Malformed ReceiveMessage envelope: " + error));
        JsonElement inner = JsonParser.parseString(envelope.message());
        SignalingException serviceError = SignalingErrorMapper.fromServiceEnvelope(inner);
        if (serviceError != null) {
            UUID errorPmid = serviceError.peerPmid() != null ? serviceError.peerPmid() : UUID.fromString(envelope.from());
            NliConstants.LOG.warn("[P2P][signaling] Service error from {}: {}", errorPmid, serviceError.getMessage());
            this.fireListeners(listener -> listener.onSignalingError(errorPmid, serviceError));
            return;
        }
        SignalingMessage parsed = SignalingMessage.CODEC.parse(JsonOps.INSTANCE, inner)
            .getOrThrow(error -> new IllegalStateException("Malformed signaling payload: " + error));
        UUID fromPmid = UUID.fromString(envelope.from());
        NliConstants.LOG.info("[P2P][signaling] Received {} session={} from {}", parsed.type(), parsed.sessionId(), fromPmid);
        switch (parsed) {
            case SignalingMessage.FriendJoin friendJoin -> this.dispatchFriendJoinMessage(fromPmid, friendJoin);
            case SignalingMessage.WebRtc webRtc -> this.dispatchWebRtcMessage(fromPmid, webRtc);
        }
    }

    private void dispatchFriendJoinMessage(UUID fromPmid, SignalingMessage.FriendJoin message) {
        FriendJoinHandler handler = this.friendJoinHandler;
        if (handler != null) {
            handler.handle(fromPmid, message);
        }
    }

    private void dispatchWebRtcMessage(UUID fromPmid, SignalingMessage.WebRtc message) {
        WebRtcSignalingHandler handler = this.webRtcSignalingHandler;
        if (handler != null) {
            handler.handle(fromPmid, message);
        }
    }

    private void fireListeners(Consumer<ConnectionListener> action) {
        for (ConnectionListener listener : this.connectionListeners) {
            action.accept(listener);
        }
    }

    private static void logNetworkPreferences(String action) {
        NliConstants.LOG.info(
            "[P2P][signaling] {} network prefs preferIPv4Stack={} preferIPv6Addresses={}",
            action,
            System.getProperty("java.net.preferIPv4Stack"),
            System.getProperty("java.net.preferIPv6Addresses")
        );
    }

    @FunctionalInterface
    public interface FriendJoinHandler {
        void handle(UUID fromPmid, SignalingMessage.FriendJoin message);
    }

    @FunctionalInterface
    public interface WebRtcSignalingHandler {
        void handle(UUID fromPmid, SignalingMessage.WebRtc message);
    }

    public interface ConnectionListener {
        default void onSignalingError(@Nullable UUID peerPmid, SignalingException cause) {
        }

        default void onSignalingConnected() {
        }

        default void onSignalingDisconnected() {
        }

        default void onSignalingConnectFailed() {
        }
    }

    private record CachedSignalingUri(String wsUrl, Instant expiresAt) {
        private static final Duration TTL = Duration.ofMinutes(5L);

        private CachedSignalingUri(String wsUrl) {
            this(wsUrl, Instant.now().plus(TTL));
        }

        private boolean isUsable() {
            return Instant.now().isBefore(this.expiresAt);
        }
    }

    private record CachedTurn(TurnAuthResult turnAuth, Instant expiresAt) {
        private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(60L);

        private CachedTurn(TurnAuthResult turnAuth) {
            this(turnAuth, Instant.now().plusSeconds(turnAuth.expirationInSeconds()));
        }

        private boolean isUsable() {
            return Instant.now().isBefore(this.expiresAt.minus(EXPIRY_MARGIN));
        }
    }

    private record ClientWebRtcMessage(String from, String message, @Nullable UUID id) {
        private static final Codec<ClientWebRtcMessage> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.STRING.fieldOf("From").forGetter(ClientWebRtcMessage::from),
                    Codec.STRING.fieldOf("Message").forGetter(ClientWebRtcMessage::message),
                    UUIDUtil.STRING_CODEC.optionalFieldOf("Id").forGetter(message -> Optional.ofNullable(message.id()))
                )
                .apply(instance, (from, message, id) -> new ClientWebRtcMessage(from, message, id.orElse(null)))
        );
    }

    private record TurnAuthResult(long expirationInSeconds, List<TurnAuthServer> turnAuthServers) {
        private static final Codec<TurnAuthResult> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.LONG.fieldOf("ExpirationInSeconds").forGetter(TurnAuthResult::expirationInSeconds),
                    TurnAuthServer.CODEC.listOf().fieldOf("TurnAuthServers").forGetter(TurnAuthResult::turnAuthServers)
                )
                .apply(instance, TurnAuthResult::new)
        );

        private RTCIceServer toRtcIceServer() {
            TurnAuthServer first = this.turnAuthServers.getFirst();
            RTCIceServer ice = new RTCIceServer();
            ice.username = first.username();
            ice.password = first.password();
            for (TurnAuthServer turnServer : this.turnAuthServers) {
                ice.urls.addAll(turnServer.urls());
            }
            return ice;
        }
    }

    private record TurnAuthServer(String username, String password, List<String> urls) {
        private static final Codec<TurnAuthServer> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.STRING.fieldOf("Username").forGetter(TurnAuthServer::username),
                    Codec.STRING.fieldOf("Password").forGetter(TurnAuthServer::password),
                    Codec.STRING.listOf().fieldOf("Urls").forGetter(TurnAuthServer::urls)
                )
                .apply(instance, TurnAuthServer::new)
        );
    }

    private enum Environment {
        STAGE("https://signaling-afd.stage-6fd5f759.franchise.minecraft-services.net"),
        PRODUCTION("https://signaling-afd.franchise.minecraft-services.net");

        private final String baseUrl;

        Environment(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        private static Optional<Environment> byName(String name) {
            return switch (name.toLowerCase(Locale.ROOT)) {
                case "stage", "staging" -> Optional.of(STAGE);
                case "prod", "production" -> Optional.of(PRODUCTION);
                default -> Optional.empty();
            };
        }

        private String getConfigurationUri() {
            return this.baseUrl + "/api/v1.0/configuration/java";
        }
    }
}
