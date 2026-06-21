package cool.muyucloud.netherlink.link.nli;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import cool.muyucloud.netherlink.link.hook.LinkContextHooks;
import cool.muyucloud.netherlink.link.model.*;
import cool.muyucloud.netherlink.link.service.LinkFriendService;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class NliFriendService implements LinkFriendService {
    private final String runtimeKey;
    private final NliApiClient api;
    private final NliRuntimeService runtimes;

    NliFriendService(String runtimeKey, NliApiClient api, NliRuntimeService runtimes) {
        this.runtimeKey = runtimeKey;
        this.api = api;
        this.runtimes = runtimes;
    }

    @Override
    public CompletableFuture<LinkFriendSnapshot> refresh() {
        return this.runtimes.session(this.runtimeKey)
            .thenCompose(session -> this.api.get("v1/friends", session.token(), this.minecraftToken()))
            .thenApply(this::snapshot);
    }

    @Override
    public CompletableFuture<LinkFriendActionOutcome> add(String name) {
        JsonObject body = new JsonObject();
        body.addProperty("name", name);
        return this.mutate("v1/friends/requests", body);
    }

    @Override
    public CompletableFuture<LinkFriendActionOutcome> remove(UUID profileId) {
        return this.runtimes.session(this.runtimeKey)
            .thenCompose(session -> this.api.delete(path("v1/friends/", profileId), session.token(), this.minecraftToken()))
            .thenApply(_ -> success(null, LinkOfficialSyncStatus.SUCCESS));
    }

    @Override
    public CompletableFuture<LinkFriendActionOutcome> accept(UUID profileId) {
        return this.mutate(path("v1/friends/requests/", profileId), null);
    }

    @Override
    public CompletableFuture<LinkFriendActionOutcome> decline(UUID profileId) {
        return this.deleteRequest(profileId);
    }

    @Override
    public CompletableFuture<LinkFriendActionOutcome> revoke(UUID profileId) {
        return this.deleteRequest(profileId);
    }

    private CompletableFuture<LinkFriendActionOutcome> deleteRequest(UUID profileId) {
        return this.runtimes.session(this.runtimeKey)
            .thenCompose(session -> this.api.delete(path("v1/friends/requests/", profileId), session.token(), this.minecraftToken()))
            .thenApply(_ -> success(null, LinkOfficialSyncStatus.SUCCESS));
    }

    private CompletableFuture<LinkFriendActionOutcome> mutate(String path, JsonObject body) {
        return this.runtimes.session(this.runtimeKey)
            .thenCompose(session -> this.api.post(path, session.token(), body, this.minecraftToken()))
            .thenApply(json -> {
                String relation = NliApiClient.string(json, "relationship", "");
                LinkFriendRelationship relationship = switch (relation) {
                    case "ACCEPTED" -> LinkFriendRelationship.FRIEND;
                    case "REQUESTED" -> LinkFriendRelationship.OUTGOING;
                    default -> null;
                };
                return success(relationship, officialSync(json));
            });
    }

    private String minecraftToken() {
        return LinkContextHooks.require(this.runtimeKey).account().getMcToken();
    }

    private LinkFriendSnapshot snapshot(JsonObject root) {
        List<LinkFriendEntry> friends = entries(array(root, "friends"), LinkFriendRelationship.FRIEND, true);
        List<LinkFriendEntry> incoming = entries(array(root, "incomingRequests"), LinkFriendRelationship.INCOMING, false);
        List<LinkFriendEntry> outgoing = entries(array(root, "outgoingRequests"), LinkFriendRelationship.OUTGOING, false);
        return new LinkFriendSnapshot(friends, incoming, outgoing);
    }

    private List<LinkFriendEntry> entries(JsonArray array, LinkFriendRelationship relationship, boolean includePresence) {
        List<LinkFriendEntry> entries = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            UUID profileId = UUID.fromString(NliApiClient.requiredString(object, "profileId"));
            List<LinkPresence> presences = includePresence ? presences(array(object, "presences")) : List.of();
            entries.add(new LinkFriendEntry(profileId, NliApiClient.nullableString(object, "name"), relationship, presences));
        }
        return List.copyOf(entries);
    }

    private List<LinkPresence> presences(JsonArray array) {
        List<LinkPresence> result = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            result.add(new LinkPresence(
                NliApiClient.requiredString(object, "presenceId"),
                status(NliApiClient.requiredString(object, "status")),
                object.has("joinable") && object.get("joinable").getAsBoolean(),
                NliApiClient.string(object, "displayText", ""),
                NliApiClient.nullableString(object, "sessionId"),
                NliApiClient.nullableString(object, "endpoint"),
                instant(object, "updatedAt"),
                instant(object, "expiresAt")
            ));
        }
        return List.copyOf(result);
    }

    private static LinkPresenceStatus status(String value) {
        return switch (value) {
            case "HOSTING" -> LinkPresenceStatus.HOSTING;
            case "ONLINE" -> LinkPresenceStatus.ONLINE;
            case "IN_GAME" -> LinkPresenceStatus.PLAYING_OFFLINE;
            case "OFFLINE" -> LinkPresenceStatus.OFFLINE;
            default -> LinkPresenceStatus.UNKNOWN;
        };
    }

    private static @org.jspecify.annotations.Nullable Instant instant(JsonObject object, String key) {
        String value = NliApiClient.nullableString(object, key);
        return value != null ? Instant.parse(value) : null;
    }

    private static JsonArray array(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray();
    }

    private static String path(String prefix, UUID profileId) {
        return prefix + URLEncoder.encode(profileId.toString(), StandardCharsets.UTF_8);
    }

    private static LinkOfficialSyncStatus officialSync(JsonObject response) {
        return switch (NliApiClient.string(response, "officialSync", "SKIPPED")) {
            case "SUCCESS" -> LinkOfficialSyncStatus.SUCCESS;
            case "FAILED" -> LinkOfficialSyncStatus.FAILED;
            case "UNSUPPORTED" -> LinkOfficialSyncStatus.UNSUPPORTED;
            default -> LinkOfficialSyncStatus.SKIPPED;
        };
    }

    private static LinkFriendActionOutcome success(LinkFriendRelationship relationship, LinkOfficialSyncStatus sync) {
        return new LinkFriendActionOutcome(LinkFriendActionResult.SUCCESS, relationship, sync, null);
    }
}
