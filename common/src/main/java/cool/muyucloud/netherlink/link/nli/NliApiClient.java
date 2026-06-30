package cool.muyucloud.netherlink.link.nli;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cool.muyucloud.netherlink.link.exception.LinkException;
import cool.muyucloud.netherlink.link.exception.LinkUnauthorizedException;
import cool.muyucloud.netherlink.link.model.LinkFailure;
import cool.muyucloud.netherlink.link.model.LinkFailureCode;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

final class NliApiClient implements AutoCloseable {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20L);

    private final URI baseUri;
    private final HttpClient http;

    NliApiClient(URI baseUri) {
        String normalized = baseUri.toString().endsWith("/") ? baseUri.toString() : baseUri + "/";
        this.baseUri = URI.create(normalized);
        this.http = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
    }

    URI websocketUri() {
        URI httpUri = this.resolve("v1/signaling/ws");
        String scheme = "https".equalsIgnoreCase(httpUri.getScheme()) ? "wss" : "ws";
        return URI.create(scheme + ":" + httpUri.toString().substring(httpUri.getScheme().length() + 1));
    }

    HttpClient http() {
        return this.http;
    }

    CompletableFuture<JsonObject> get(String path, String bearer, @Nullable String minecraftToken) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(this.resolve(path)).GET();
        addMinecraftToken(builder, minecraftToken);
        return this.parseJson(this.sendRaw(builder, bearer));
    }

    CompletableFuture<String> getPublicText(String path, String language) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(this.resolve(path))
            .GET()
            .header("Accept", "text/plain");
        if (language != null && !language.isBlank()) {
            builder.header("Accept-Language", language);
        }
        return this.sendRaw(builder).thenApply(HttpResponse::body);
    }

    CompletableFuture<JsonObject> post(String path, String bearer, @Nullable JsonObject body) {
        return this.send(HttpRequest.newBuilder(this.resolve(path)), bearer, body, false);
    }

    CompletableFuture<JsonObject> post(String path, String bearer, @Nullable JsonObject body, @Nullable String minecraftToken) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(this.resolve(path));
        addMinecraftToken(builder, minecraftToken);
        return this.send(builder, bearer, body, false);
    }

    CompletableFuture<JsonObject> put(String path, String bearer, JsonObject body) {
        return this.send(HttpRequest.newBuilder(this.resolve(path)), bearer, body, true);
    }

    CompletableFuture<JsonObject> put(String path, String bearer, JsonObject body, @Nullable String minecraftToken) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(this.resolve(path));
        addMinecraftToken(builder, minecraftToken);
        return this.send(builder, bearer, body, true);
    }

    CompletableFuture<Void> delete(String path, String bearer, @Nullable String minecraftToken) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(this.resolve(path)).DELETE();
        addMinecraftToken(builder, minecraftToken);
        return this.sendRaw(builder, bearer).thenApply(_ -> null);
    }

    private static void addMinecraftToken(HttpRequest.Builder builder, @Nullable String minecraftToken) {
        if (minecraftToken != null && !minecraftToken.isBlank()) {
            builder.header("X-Minecraft-Access-Token", minecraftToken);
        }
    }

    private CompletableFuture<JsonObject> send(HttpRequest.Builder builder, String bearer, @Nullable JsonObject body, boolean put) {
        String json = body == null ? "{}" : body.toString();
        builder.header("Content-Type", "application/json");
        if (put) {
            builder.PUT(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        } else {
            builder.POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        }
        return this.parseJson(this.sendRaw(builder, bearer));
    }

    private CompletableFuture<JsonObject> parseJson(CompletableFuture<HttpResponse<String>> responseFuture) {
        return responseFuture.thenApply(response -> {
            if (response.body() == null || response.body().isBlank()) {
                return new JsonObject();
            }
            try {
                JsonElement parsed = JsonParser.parseString(response.body());
                if (!parsed.isJsonObject()) {
                    throw new IllegalStateException("NLI response is not a JSON object");
                }
                return parsed.getAsJsonObject();
            } catch (RuntimeException error) {
                throw new CompletionException(new LinkException(
                    new LinkFailure(LinkFailureCode.INTERNAL, "NLI returned malformed JSON", false),
                    error
                ));
            }
        });
    }

    private CompletableFuture<HttpResponse<String>> sendRaw(HttpRequest.Builder builder, String bearer) {
        if (bearer == null || bearer.isBlank()) {
            return CompletableFuture.failedFuture(new LinkUnauthorizedException("NLI credential is missing"));
        }
        return this.sendRaw(builder
            .header("Accept", "application/json")
            .header("Authorization", "Bearer " + bearer));
    }

    private CompletableFuture<HttpResponse<String>> sendRaw(HttpRequest.Builder builder) {
        HttpRequest request = builder.timeout(REQUEST_TIMEOUT).build();
        return this.http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            .handle((response, error) -> {
                if (error != null) {
                    Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                    if (cause instanceof IOException) {
                        throw new CompletionException(new LinkException(
                            new LinkFailure(LinkFailureCode.NETWORK, "NLI request failed", true),
                            cause
                        ));
                    }
                    throw new CompletionException(cause);
                }
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new CompletionException(this.error(response.statusCode(), response.body()));
                }
                return response;
            });
    }

    private RuntimeException error(int status, @Nullable String body) {
        String code = "";
        String message = "NLI request failed with HTTP " + status;
        try {
            if (body != null && !body.isBlank()) {
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                code = string(json, "code", "");
                message = string(json, "message", message);
            }
        } catch (RuntimeException ignored) {
        }
        if (status == 401 || "INVALID_INSTANCE_TOKEN".equals(code)) {
            return new LinkUnauthorizedException(message);
        }
        LinkFailureCode failureCode = switch (code) {
            case "RATE_LIMITED" -> LinkFailureCode.RATE_LIMITED;
            case "PROFILE_NOT_FOUND", "PLAYER_NOT_FOUND" -> LinkFailureCode.PROFILE_NOT_FOUND;
            case "ALREADY_FRIENDS" -> LinkFailureCode.ALREADY_FRIENDS;
            case "REQUEST_NOT_FOUND" -> LinkFailureCode.REQUEST_NOT_FOUND;
            case "NOT_FRIENDS" -> LinkFailureCode.NOT_FRIENDS;
            case "TARGET_UNAVAILABLE" -> LinkFailureCode.TARGET_UNAVAILABLE;
            case "TARGET_NOT_JOINABLE" -> LinkFailureCode.TARGET_NOT_JOINABLE;
            case "SESSION_NOT_FOUND", "INVALID_SESSION_STATE" -> LinkFailureCode.INVALID_SESSION;
            case "CONNECTION_LIMIT", "INSTANCE_LIMIT_REACHED" -> LinkFailureCode.CONNECTION_LIMIT;
            case "FORBIDDEN", "OFFICIAL_FRIENDS_FORBIDDEN" -> LinkFailureCode.FORBIDDEN;
            case "SERVICE_UNAVAILABLE" -> LinkFailureCode.SERVICE_UNAVAILABLE;
            default -> status == 429 ? LinkFailureCode.RATE_LIMITED
                : status == 403 ? LinkFailureCode.FORBIDDEN
                : status >= 500 ? LinkFailureCode.SERVICE_UNAVAILABLE
                : LinkFailureCode.UNKNOWN;
        };
        boolean retryable = status == 429 || status >= 500;
        return new LinkException(new LinkFailure(failureCode, message, retryable));
    }

    private URI resolve(String path) {
        return this.baseUri.resolve(path.startsWith("/") ? path.substring(1) : path);
    }

    static String requiredString(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            throw new IllegalStateException("NLI response is missing " + key);
        }
        return object.get(key).getAsString();
    }

    static @Nullable String nullableString(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : null;
    }

    static String string(JsonObject object, String key, String fallback) {
        String value = nullableString(object, key);
        return value != null ? value : fallback;
    }

    @Override
    public void close() {
        this.http.close();
    }
}
