package cool.muyucloud.netherlink.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cool.muyucloud.netherlink.NliConstants;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PresencePublisher {
    private static final URI PRESENCE_URI = URI.create("https://api.minecraftservices.com/presence");
    private final HttpClient http = HttpClient.newHttpClient();

    public Map<UUID, UUID> publish(MinecraftAccount account) {
        NliConstants.LOG.info("Publishing NetherLink presence as PLAYING_HOSTED_SERVER for {}", account.getMcProfileName());
        return presence(account, PresenceStatus.PLAYING_HOSTED_SERVER, true);
    }

    public void revoke(MinecraftAccount account) {
        NliConstants.LOG.info("Revoking NetherLink presence for {}", account.getMcProfileName());
        presence(account, PresenceStatus.OFFLINE, false);
    }

    private Map<UUID, UUID> presence(MinecraftAccount account, PresenceStatus status, boolean includeJoinInfo) {
        String token = account.getMcToken();
        if (token == null || token.isBlank()) {
            throw new NetherLinkAuthException("Minecraft access token was not found");
        }

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("status", status.name());
        if (includeJoinInfo) {
            JsonObject joinInfo = new JsonObject();
            joinInfo.add("value", JsonNull.INSTANCE);
            joinInfo.add("invites", new JsonArray());
            requestBody.add("joinInfo", joinInfo);
        }

        HttpRequest request = HttpRequest.newBuilder(PRESENCE_URI)
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
            .build();
        try {
            HttpResponse<String> response = this.http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                NliConstants.LOG.warn("Presence {} failed: HTTP {}", status, response.statusCode());
                return Map.of();
            }
            Map<UUID, UUID> peers = parsePresence(response.body());
            NliConstants.LOG.info("Presence {} returned {} peer mappings", status, peers.size());
            return peers;
        } catch (IOException e) {
            NliConstants.LOG.warn("Presence {} failed: {}", status, e.toString());
            return Map.of();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            NliConstants.LOG.warn("Presence {} interrupted", status);
            return Map.of();
        }
    }

    private static Map<UUID, UUID> parsePresence(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonArray presence = root.has("presence") && root.get("presence").isJsonArray()
            ? root.getAsJsonArray("presence")
            : new JsonArray();
        Map<UUID, UUID> peers = new ConcurrentHashMap<>();
        presence.forEach(element -> {
            if (!element.isJsonObject()) {
                return;
            }
            JsonObject entry = element.getAsJsonObject();
            UUID pmid = parseUuid(entry, "pmid");
            UUID profileId = parseUuid(entry, "profileId");
            if (pmid != null && profileId != null) {
                peers.putIfAbsent(pmid, profileId);
            }
        });
        return Map.copyOf(peers);
    }

    private static @Nullable UUID parseUuid(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            return null;
        }
        try {
            return UUID.fromString(object.get(key).getAsString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private enum PresenceStatus {
        PLAYING_HOSTED_SERVER,
        OFFLINE
    }
}
