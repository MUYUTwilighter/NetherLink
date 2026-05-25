package cool.muyucloud.netherlink.p2p;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import cool.muyucloud.netherlink.NliConstants;
import net.minecraft.server.jsonrpc.JsonRPCErrors;
import net.minecraft.server.jsonrpc.JsonRPCUtils;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public final class JsonRpcClient implements WebSocket.Listener {
    private static final int MAX_MESSAGE_BYTES = 65536;
    private final ScheduledExecutorService executor;
    private final MethodHandler methodHandler;
    private final Runnable onDisconnect;
    private final Map<Integer, CompletableFuture<JsonElement>> pendingRequests = new HashMap<>();
    private final StringBuilder messageBuffer = new StringBuilder();
    private CompletableFuture<Void> sendChain = CompletableFuture.completedFuture(null);
    private @Nullable WebSocket webSocket;
    private int transactionId;

    public JsonRpcClient(ScheduledExecutorService executor, MethodHandler methodHandler, Runnable onDisconnect) {
        this.executor = executor;
        this.methodHandler = methodHandler;
        this.onDisconnect = onDisconnect;
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        this.executor.execute(() -> {
            this.webSocket = webSocket;
            NliConstants.LOG.info("[P2P][jsonrpc] WebSocket opened");
            webSocket.request(1L);
        });
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        this.executor.execute(() -> this.teardown(new IOException("Signaling WebSocket closed (code=" + statusCode + ", reason=" + reason + ")"), true));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence chars, boolean last) {
        String slice = chars.toString();
        this.executor.execute(() -> {
            this.appendAndDispatch(slice, last);
            webSocket.request(1L);
        });
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        this.executor.execute(() -> this.teardown(new IOException("Signaling WebSocket errored", error), true));
    }

    public void sendNotification(String method) {
        this.executor.execute(() -> this.send(createRequest(null, method, List.of()).toString()));
    }

    public void sendResponse(JsonElement id, JsonElement result) {
        this.executor.execute(() -> this.send(JsonRPCUtils.createSuccessResult(id, result).toString()));
    }

    public void sendError(JsonElement id, JsonRPCErrors error, String data) {
        this.executor.execute(() -> this.send(error.create(id, data).toString()));
    }

    public CompletableFuture<JsonElement> sendRequest(String method, List<JsonElement> params, @Nullable Duration timeout) {
        CompletableFuture<JsonElement> future = new CompletableFuture<>();
        this.executor.execute(() -> {
            WebSocket ws = this.webSocket;
            if (ws == null) {
                NliConstants.LOG.warn("[P2P][jsonrpc] Cannot send request {}; websocket is not connected", method);
                future.completeExceptionally(new IOException("WebSocket is not connected"));
                return;
            }
            int id = ++this.transactionId;
            String payload = createRequest(id, method, params).toString();
            this.pendingRequests.put(id, future);
            NliConstants.LOG.info("[P2P][jsonrpc] Sending request id={} method={}", id, method);
            if (timeout != null && !timeout.isZero() && !timeout.isNegative()) {
                ScheduledFuture<?> timeoutTask = this.executor.schedule(() -> {
                    CompletableFuture<JsonElement> pending = this.pendingRequests.remove(id);
                    if (pending != null) {
                        NliConstants.LOG.warn("[P2P][jsonrpc] Request timed out id={} method={} after {}ms", id, method, timeout.toMillis());
                        pending.completeExceptionally(new TimeoutException("JSON-RPC request timed out: id=" + id + " method=" + method));
                    }
                }, timeout.toMillis(), TimeUnit.MILLISECONDS);
                future.whenComplete((ignored, error) -> timeoutTask.cancel(false));
            }
            this.sendChain = this.sendChain.<Void>thenCompose(ignored -> ws.sendText(payload, true).thenApply(sent -> {
                NliConstants.LOG.info("[P2P][jsonrpc] Sent request id={} method={}", id, method);
                return null;
            })).exceptionally(error -> {
                this.executor.execute(() -> {
                    CompletableFuture<JsonElement> pending = this.pendingRequests.remove(id);
                    if (pending != null) {
                        NliConstants.LOG.warn("[P2P][jsonrpc] Send failed id={} method={}: {}", id, method, error.toString());
                        pending.completeExceptionally(new IOException("WebSocket send failed", error));
                    }
                });
                return null;
            });
        });
        return future;
    }

    public CompletableFuture<?> close() {
        CompletableFuture<?> done = new CompletableFuture<>();
        this.executor.execute(() -> {
            WebSocket ws = this.webSocket;
            this.teardown(new IOException("JSON-RPC client closed"), false);
            if (ws != null && !ws.isOutputClosed()) {
                ws.sendClose(1000, "shutdown").whenComplete((ignored, error) -> done.complete(null));
            } else {
                done.complete(null);
            }
        });
        return done;
    }

    private void send(String payload) {
        WebSocket ws = this.webSocket;
        if (ws != null) {
            this.sendChain = this.sendChain.<Void>thenCompose(ignored -> ws.sendText(payload, true).thenApply(sent -> null)).exceptionally(error -> {
                NliConstants.LOG.warn("WebSocket send failed", error);
                return null;
            });
        }
    }

    private void appendAndDispatch(String slice, boolean last) {
        if (this.messageBuffer.length() + slice.length() > MAX_MESSAGE_BYTES) {
            NliConstants.LOG.warn("JSON-RPC message exceeded {} bytes, dropping", MAX_MESSAGE_BYTES);
            this.messageBuffer.setLength(0);
            return;
        }
        this.messageBuffer.append(slice);
        if (last) {
            String full = this.messageBuffer.toString();
            this.messageBuffer.setLength(0);
            try {
                this.dispatch(full);
            } catch (RuntimeException e) {
                NliConstants.LOG.error("Failed to handle JSON-RPC message: {}", full, e);
            }
        }
    }

    private void dispatch(String text) {
        JsonElement root = JsonParser.parseString(text);
        if (!root.isJsonObject()) {
            return;
        }
        JsonObject object = root.getAsJsonObject();
        JsonElement id = JsonRPCUtils.getRequestId(object);
        boolean hasId = id != null && !id.isJsonNull();
        String method = JsonRPCUtils.getMethodName(object);
        JsonElement result = JsonRPCUtils.getResult(object);
        JsonObject error = JsonRPCUtils.getError(object);
        if (method != null && result == null && error == null) {
            NliConstants.LOG.debug("[P2P][jsonrpc] Received request method={}", method);
            this.methodHandler.onMethod(this, hasId ? id : null, method, JsonRPCUtils.getParams(object));
        } else if (method == null && error == null && result != null && hasId && isValidResponseId(id)) {
            CompletableFuture<JsonElement> pending = this.pendingRequests.remove(id.getAsInt());
            if (pending != null) {
                NliConstants.LOG.info("[P2P][jsonrpc] Received response id={}", id.getAsInt());
                pending.complete(result);
            } else {
                NliConstants.LOG.warn("[P2P][jsonrpc] Received response for unknown request id={}", id.getAsInt());
            }
        } else if (method == null && result == null && error != null) {
            this.handleErrorResponse(hasId ? id : null, error);
        }
    }

    private void handleErrorResponse(@Nullable JsonElement id, JsonObject error) {
        int code = error.has("code") ? error.get("code").getAsInt() : 0;
        String message = error.has("message") ? error.get("message").getAsString() : "";
        JsonElement data = error.get("data");
        if (id != null && isValidResponseId(id)) {
            CompletableFuture<JsonElement> pending = this.pendingRequests.remove(id.getAsInt());
            if (pending != null) {
                NliConstants.LOG.warn("[P2P][jsonrpc] Received error response id={} code={} message={}", id.getAsInt(), code, message);
                pending.completeExceptionally(new JsonRpcException(code, message, data));
            }
        }
    }

    private static boolean isValidResponseId(JsonElement id) {
        return id instanceof JsonPrimitive primitive && primitive.isNumber();
    }

    private static JsonObject createRequest(@Nullable Integer id, String method, List<JsonElement> params) {
        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0");
        if (id != null) {
            request.addProperty("id", id);
        }
        request.addProperty("method", method);
        if (!params.isEmpty()) {
            com.google.gson.JsonArray array = new com.google.gson.JsonArray(params.size());
            params.forEach(array::add);
            request.add("params", array);
        }
        return request;
    }

    private void teardown(Throwable cause, boolean fireDisconnect) {
        this.webSocket = null;
        this.pendingRequests.values().forEach(pending -> pending.completeExceptionally(cause));
        this.pendingRequests.clear();
        if (fireDisconnect) {
            this.onDisconnect.run();
        }
    }

    @FunctionalInterface
    public interface MethodHandler {
        void onMethod(JsonRpcClient rpc, @Nullable JsonElement id, String method, @Nullable JsonElement params);
    }
}
