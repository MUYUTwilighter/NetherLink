package cool.muyucloud.netherlink.link.official;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.account.MinecraftAccount;
import cool.muyucloud.netherlink.account.NetherLinkAuthException;
import cool.muyucloud.netherlink.http.JsonHttp;
import cool.muyucloud.netherlink.link.exception.LinkUnauthorizedException;
import cool.muyucloud.netherlink.link.hook.LinkContextHooks;
import cool.muyucloud.netherlink.link.model.LinkPresence;
import cool.muyucloud.netherlink.link.model.LinkPresenceStatus;
import cool.muyucloud.netherlink.link.model.LinkPresenceUpdate;
import cool.muyucloud.netherlink.link.service.LinkPresenceService;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class OfficialPresenceService implements LinkPresenceService {
    private static final URI PRESENCE_URI = URI.create("https://api.minecraftservices.com/presence");

    private final HttpClient http = HttpClient.newHttpClient();

    @Override
    public LinkPresence publish(String runtimeKey, LinkPresenceUpdate update) {
        MinecraftAccount account = LinkContextHooks.require(runtimeKey).account();
        NliConstants.LOG.info("Publishing NetherLink presence as PLAYING_HOSTED_SERVER for {}", account.getMcProfileName());
        return publishHosting(account, update).presence();
    }

    @Override
    public void revoke(String runtimeKey) {
        MinecraftAccount account = LinkContextHooks.require(runtimeKey).account();
        NliConstants.LOG.info("Revoking NetherLink presence for {}", account.getMcProfileName());
        presence(account, PresenceStatus.OFFLINE);
    }

    OfficialPresenceResult publishHosting(MinecraftAccount account, LinkPresenceUpdate update) {
        NliConstants.LOG.debug("Official Presence ignores display text: {}", update.displayText());
        Map<String, UUID> peers = presence(account, PresenceStatus.PLAYING_HOSTED_SERVER);
        LinkPresence published = new LinkPresence(null, LinkPresenceStatus.HOSTING, true, update.displayText(), null, null, null, null);
        return new OfficialPresenceResult(peers, published);
    }

    private Map<String, UUID> presence(MinecraftAccount account, PresenceStatus status) {
        String token = account.getMcToken();
        if (token == null || token.isBlank()) {
            throw new NetherLinkAuthException("Minecraft access token was not found");
        }

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("status", status.name());

        HttpRequest request = HttpRequest.newBuilder(PRESENCE_URI)
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .POST(JsonHttp.jsonBody(requestBody))
            .build();
        try {
            HttpResponse<String> response = JsonHttp.sendString(this.http, request);
            if (response.statusCode() == 401) {
                throw new LinkUnauthorizedException("Presence " + status + " failed: HTTP 401");
            }
            if (!JsonHttp.isSuccess(response.statusCode())) {
                NliConstants.LOG.warn("Presence {} failed: HTTP {}", status, response.statusCode());
                return Map.of();
            }
            Map<String, UUID> peers = parsePresence(response.body());
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

    private static Map<String, UUID> parsePresence(String body) {
        JsonObject root = JsonHttp.parseObject(body);
        JsonArray presence = root.has("presence") && root.get("presence").isJsonArray()
            ? root.getAsJsonArray("presence")
            : new JsonArray();
        Map<String, UUID> peers = new HashMap<>();
        presence.forEach(element -> {
            if (!element.isJsonObject()) {
                return;
            }
            JsonObject entry = element.getAsJsonObject();
            UUID pmid = JsonHttp.uuid(entry, "pmid");
            UUID profileId = JsonHttp.uuid(entry, "profileId");
            String status = JsonHttp.string(entry, "status", "OFFLINE");
            if (pmid != null && profileId != null) {
                peers.putIfAbsent(pmid.toString(), profileId);
            }
            NliConstants.LOG.debug("Official presence profile={} pmid={} status={} lastUpdated={}", profileId, pmid, status, JsonHttp.instant(entry, "lastUpdated"));
        });
        return Map.copyOf(peers);
    }

    private enum PresenceStatus {
        PLAYING_HOSTED_SERVER,
        OFFLINE
    }

    record OfficialPresenceResult(Map<String, UUID> profileIdsByPresence, LinkPresence presence) {
    }
}
