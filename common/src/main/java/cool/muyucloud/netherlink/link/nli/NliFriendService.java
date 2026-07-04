package cool.muyucloud.netherlink.link.nli;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import cool.muyucloud.netherlink.http.JsonHttp;
import cool.muyucloud.netherlink.link.hook.LinkContextHooks;
import cool.muyucloud.netherlink.link.model.*;
import cool.muyucloud.netherlink.link.service.LinkFriendService;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
    public CompletableFuture<LinkFriendSettings> settings() {
        return this.runtimes.session(this.runtimeKey)
            .thenCompose(session -> this.api.get("v1/friends/settings", session.token(), this.minecraftToken()))
            .thenApply(NliFriendService::settings);
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
            .thenApply(ignored1 -> success(null, LinkOfficialSyncStatus.SUCCESS));
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

    @Override
    public CompletableFuture<LinkFriendSettings> updateSettings(LinkFriendSettings settings) {
        JsonObject body = new JsonObject();
        body.addProperty("friendsEnabled", settings.friendsEnabled());
        body.addProperty("acceptInvites", settings.acceptInvites());
        return this.runtimes.session(this.runtimeKey)
            .thenCompose(session -> this.api.put("v1/friends/settings", session.token(), body, this.minecraftToken()))
            .thenApply(json -> new LinkFriendSettings(
                JsonHttp.bool(json, "friendsEnabled", settings.friendsEnabled()),
                JsonHttp.bool(json, "acceptInvites", settings.acceptInvites())
            ));
    }

    private static LinkFriendSettings settings(JsonObject json) {
        return new LinkFriendSettings(
            JsonHttp.bool(json, "friendsEnabled"),
            JsonHttp.bool(json, "acceptInvites")
        );
    }

    private CompletableFuture<LinkFriendActionOutcome> deleteRequest(UUID profileId) {
        return this.runtimes.session(this.runtimeKey)
            .thenCompose(session -> this.api.delete(path("v1/friends/requests/", profileId), session.token(), this.minecraftToken()))
            .thenApply(ignored2 -> success(null, LinkOfficialSyncStatus.SUCCESS));
    }

    private CompletableFuture<LinkFriendActionOutcome> mutate(String path, JsonObject body) {
        return this.runtimes.session(this.runtimeKey)
            .thenCompose(session -> this.api.post(path, session.token(), body, this.minecraftToken()))
            .thenApply(json -> {
                String relation = JsonHttp.string(json, "relationship", "");
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
        List<LinkPresence> selfPresences = presences(JsonHttp.array(root, "selfPresences"));
        List<LinkFriendEntry> friends = entries(JsonHttp.array(root, "friends"), LinkFriendRelationship.FRIEND, true);
        List<LinkFriendEntry> incoming = entries(JsonHttp.array(root, "incomingRequests"), LinkFriendRelationship.INCOMING, false);
        List<LinkFriendEntry> outgoing = entries(JsonHttp.array(root, "outgoingRequests"), LinkFriendRelationship.OUTGOING, false);
        return new LinkFriendSnapshot(selfPresences, friends, incoming, outgoing);
    }

    private List<LinkFriendEntry> entries(JsonArray array, LinkFriendRelationship relationship, boolean includePresence) {
        List<LinkFriendEntry> entries = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            UUID profileId = JsonHttp.requiredUuid(object, "profileId");
            List<LinkPresence> presences = includePresence ? presences(JsonHttp.array(object, "presences")) : List.of();
            entries.add(new LinkFriendEntry(profileId, JsonHttp.string(object, "name"), relationship, presences));
        }
        return List.copyOf(entries);
    }

    private List<LinkPresence> presences(JsonArray array) {
        List<LinkPresence> result = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            result.add(new LinkPresence(
                JsonHttp.requiredString(object, "presenceId"),
                status(JsonHttp.requiredString(object, "status")),
                JsonHttp.bool(object, "joinable"),
                JsonHttp.string(object, "displayText", ""),
                JsonHttp.string(object, "sessionId"),
                JsonHttp.string(object, "endpoint"),
                JsonHttp.strictInstant(object, "updatedAt"),
                JsonHttp.strictInstant(object, "expiresAt")
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


    private static String path(String prefix, UUID profileId) {
        return prefix + URLEncoder.encode(profileId.toString(), StandardCharsets.UTF_8);
    }

    private static LinkOfficialSyncStatus officialSync(JsonObject response) {
        return switch (JsonHttp.string(response, "officialSync", "SKIPPED")) {
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
