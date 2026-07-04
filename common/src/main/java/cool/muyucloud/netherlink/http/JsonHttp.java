package cool.muyucloud.netherlink.http;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class JsonHttp {
    private JsonHttp() {
    }

    public static HttpRequest.BodyPublisher jsonBody(@Nullable JsonObject body) {
        return HttpRequest.BodyPublishers.ofString(body == null ? "{}" : body.toString(), StandardCharsets.UTF_8);
    }

    public static HttpResponse<String> sendString(HttpClient http, HttpRequest request) throws IOException, InterruptedException {
        return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    public static JsonResponse sendJson(HttpClient http, HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = sendString(http, request);
        return new JsonResponse(response.statusCode(), response.headers(), parseObject(response.body()));
    }

    public static boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    public static JsonObject parseObject(@Nullable String body) {
        if (body == null || body.isBlank()) {
            return new JsonObject();
        }
        JsonElement parsed = JsonParser.parseString(body);
        if (!parsed.isJsonObject()) {
            throw new IllegalStateException("HTTP response is not a JSON object");
        }
        return parsed.getAsJsonObject();
    }

    public static String requiredString(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            throw new IllegalStateException("HTTP response is missing " + key);
        }
        return object.get(key).getAsString();
    }

    public static long requiredLong(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            throw new IllegalStateException("HTTP response is missing " + key);
        }
        return object.get(key).getAsLong();
    }

    public static UUID requiredUuid(JsonObject object, String key) {
        return UUID.fromString(requiredString(object, key));
    }

    public static Instant requiredInstant(JsonObject object, String key) {
        return Instant.parse(requiredString(object, key));
    }

    public static @Nullable String string(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            return null;
        }
        return object.get(key).getAsString();
    }

    public static Optional<String> stringOptional(JsonObject object, String key) {
        String value = string(object, key);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    public static String string(JsonObject object, String key, String fallback) {
        String value = string(object, key);
        return value != null ? value : fallback;
    }

    public static long longValue(JsonObject object, String key, long fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        return object.get(key).getAsLong();
    }

    public static boolean bool(JsonObject object, String key) {
        return bool(object, key, false);
    }

    public static boolean bool(JsonObject object, String key, boolean fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        return object.get(key).getAsBoolean();
    }

    public static JsonArray array(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray();
    }

    public static JsonObject object(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonObject() ? object.getAsJsonObject(key) : new JsonObject();
    }

    public static @Nullable Instant instant(JsonObject object, String key) {
        String value = string(object, key);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static @Nullable Instant strictInstant(JsonObject object, String key) {
        String value = string(object, key);
        return value == null ? null : Instant.parse(value);
    }

    public static @Nullable UUID uuid(JsonObject object, String key) {
        String value = string(object, key);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static @Nullable UUID uuidLenient(JsonObject object, String key) {
        String value = string(object, key);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            String compact = value.replace("-", "");
            if (compact.length() != 32) {
                return null;
            }
            String normalized = compact.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                "$1-$2-$3-$4-$5"
            ).toLowerCase(Locale.ROOT);
            try {
                return UUID.fromString(normalized);
            } catch (IllegalArgumentException ignoredAgain) {
                return null;
            }
        }
    }

    public static Optional<Duration> retryAfter(HttpResponse<?> response) {
        return retryAfter(response.headers());
    }

    public static Optional<Duration> retryAfter(HttpHeaders headers) {
        return headers.firstValue("Retry-After").flatMap(value -> {
            try {
                return Optional.of(Duration.ofSeconds(Long.parseLong(value.trim())));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        });
    }

    public record JsonResponse(int statusCode, HttpHeaders headers, JsonObject body) {
        public boolean isSuccess() {
            return JsonHttp.isSuccess(this.statusCode);
        }
    }
}
