package cool.muyucloud.netherlink.link.official;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.account.MinecraftAccount;
import cool.muyucloud.netherlink.account.NetherLinkAuthException;
import cool.muyucloud.netherlink.link.model.*;
import cool.muyucloud.netherlink.link.service.LinkFriendService;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class OfficialFriendService implements LinkFriendService {
    private static final URI FRIENDS_URI = URI.create("https://api.minecraftservices.com/friends");
    private static final URI PRESENCE_URI = URI.create("https://api.minecraftservices.com/presence");
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "NetherLink Friends");
        thread.setDaemon(true);
        return thread;
    });

    private final HttpClient http = HttpClient.newHttpClient();
    private final String accessToken;
    private @Nullable String friendsEtag;
    private @Nullable String presenceEtag;
    private FriendLists friendCache = new FriendLists(List.of(), List.of(), List.of());
    private LinkFriendSnapshot cache = new LinkFriendSnapshot(List.of(), List.of(), List.of());

    public OfficialFriendService(MinecraftAccount account) {
        String token = account.getMcToken();
        if (token == null || token.isBlank()) {
            throw new NetherLinkAuthException("Minecraft access token was not found");
        }
        this.accessToken = token;
    }

    @Override
    public CompletableFuture<LinkFriendSnapshot> refresh() {
        return CompletableFuture.supplyAsync(() -> {
            FriendLists lists = this.getFriends();
            this.friendCache = lists;
            Map<UUID, Presence> presenceByProfile = this.getPresence();
            List<LinkFriendEntry> friends = entries(lists.friends(), LinkFriendRelationship.FRIEND, presenceByProfile);
            List<LinkFriendEntry> incoming = entries(lists.incoming(), LinkFriendRelationship.INCOMING, presenceByProfile);
            List<LinkFriendEntry> outgoing = entries(lists.outgoing(), LinkFriendRelationship.OUTGOING, presenceByProfile);
            this.cache = new LinkFriendSnapshot(friends, incoming, outgoing);
            return this.cache;
        }, EXECUTOR);
    }

    @Override
    public CompletableFuture<LinkFriendActionOutcome> add(String name) {
        return CompletableFuture.supplyAsync(() -> this.putFriendAction(name, null, "ADD"), EXECUTOR);
    }

    @Override
    public CompletableFuture<LinkFriendActionOutcome> remove(UUID profileId) {
        return CompletableFuture.supplyAsync(() -> this.putFriendAction(null, profileId, "REMOVE"), EXECUTOR);
    }

    @Override
    public CompletableFuture<LinkFriendActionOutcome> accept(UUID profileId) {
        return CompletableFuture.supplyAsync(() -> this.putFriendAction(null, profileId, "ADD"), EXECUTOR);
    }

    @Override
    public CompletableFuture<LinkFriendActionOutcome> decline(UUID profileId) {
        return CompletableFuture.supplyAsync(() -> this.putFriendAction(null, profileId, "REMOVE"), EXECUTOR);
    }

    @Override
    public CompletableFuture<LinkFriendActionOutcome> revoke(UUID profileId) {
        return CompletableFuture.supplyAsync(() -> this.putFriendAction(null, profileId, "REMOVE"), EXECUTOR);
    }

    private FriendLists getFriends() {
        HttpRequest.Builder builder = this.authorized(FRIENDS_URI).GET();
        if (this.friendsEtag != null) {
            builder.header("If-None-Match", this.friendsEtag);
        }
        try {
            HttpResponse<String> response = this.http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 304) {
                return this.friendCache;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Friend list request failed: " + handleHttpStatus(response.statusCode()));
            }
            this.friendsEtag = response.headers().firstValue("ETag").orElse(null);
            return parseFriendLists(response.body());
        } catch (IOException e) {
            throw new IllegalStateException("Friend list request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Friend list request interrupted", e);
        }
    }

    private Map<UUID, Presence> getPresence() {
        JsonObject body = new JsonObject();
        body.addProperty("status", "ONLINE");
        HttpRequest.Builder builder = this.authorized(PRESENCE_URI)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (this.presenceEtag != null) {
            builder.header("If-None-Match", this.presenceEtag);
        }
        try {
            HttpResponse<String> response = this.http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 304) {
                Map<UUID, Presence> cached = new HashMap<>();
                this.cache.all().forEach(entry -> {
                    if (!entry.presences().isEmpty()) {
                        LinkPresence presence = entry.presences().getFirst();
                        cached.put(entry.profileId(), new Presence(presence.presenceId(), presence.status(), presence.joinable()));
                    }
                });
                return cached;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                NliConstants.LOG.warn("Friend presence request failed: HTTP {}", response.statusCode());
                return Map.of();
            }
            this.presenceEtag = response.headers().firstValue("ETag").orElse(null);
            return parsePresence(response.body());
        } catch (IOException e) {
            NliConstants.LOG.warn("Friend presence request failed: {}", e.toString());
            return Map.of();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Map.of();
        }
    }

    private LinkFriendActionOutcome putFriendAction(@Nullable String name, @Nullable UUID profileId, String updateType) {
        JsonObject body = new JsonObject();
        if (name != null) {
            body.addProperty("name", name);
        }
        if (profileId != null) {
            body.addProperty("profileId", profileId.toString());
        }
        body.addProperty("updateType", updateType);

        HttpRequest request = this.authorized(FRIENDS_URI)
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build();
        try {
            HttpResponse<String> response = this.http.send(request, HttpResponse.BodyHandlers.ofString());
            LinkFriendActionResult result = handleHttpStatus(response.statusCode());
            if (result == LinkFriendActionResult.SUCCESS) {
                this.friendsEtag = null;
                this.friendCache = parseFriendLists(response.body());
                this.cache = this.friendCache.snapshot(Map.of());
            }
            return new LinkFriendActionOutcome(
                result,
                result == LinkFriendActionResult.SUCCESS ? this.relationshipAfterAction(name, profileId) : null,
                result == LinkFriendActionResult.SUCCESS ? LinkOfficialSyncStatus.SUCCESS : LinkOfficialSyncStatus.FAILED,
                failureFor(result)
            );
        } catch (IOException e) {
            NliConstants.LOG.warn("Friend action failed: {}", e.toString());
            return new LinkFriendActionOutcome(
                LinkFriendActionResult.ERROR,
                null,
                LinkOfficialSyncStatus.FAILED,
                new LinkFailure(LinkFailureCode.NETWORK, "Friend action network request failed", true)
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new LinkFriendActionOutcome(
                LinkFriendActionResult.ERROR,
                null,
                LinkOfficialSyncStatus.FAILED,
                new LinkFailure(LinkFailureCode.CANCELLED, "Friend action was interrupted", true)
            );
        }
    }

    private @Nullable LinkFriendRelationship relationshipAfterAction(@Nullable String name, @Nullable UUID profileId) {
        if (contains(this.friendCache.friends(), name, profileId)) {
            return LinkFriendRelationship.FRIEND;
        }
        if (contains(this.friendCache.incoming(), name, profileId)) {
            return LinkFriendRelationship.INCOMING;
        }
        if (contains(this.friendCache.outgoing(), name, profileId)) {
            return LinkFriendRelationship.OUTGOING;
        }
        return null;
    }

    private static boolean contains(List<Friend> friends, @Nullable String name, @Nullable UUID profileId) {
        return friends.stream().anyMatch(friend ->
            profileId != null ? profileId.equals(friend.profileId()) : name != null && name.equalsIgnoreCase(friend.name())
        );
    }

    private HttpRequest.Builder authorized(URI uri) {
        return HttpRequest.newBuilder(uri).header("Authorization", "Bearer " + this.accessToken);
    }

    private static FriendLists parseFriendLists(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        return new FriendLists(
            parseFriends(root, "friends"),
            parseFriends(root, "incomingRequests"),
            parseFriends(root, "outgoingRequests")
        );
    }

    private static List<Friend> parseFriends(JsonObject root, String key) {
        if (!root.has(key) || !root.get(key).isJsonArray()) {
            return List.of();
        }
        List<Friend> friends = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray(key)) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            UUID profileId = parseUuid(object, "profileId");
            String name = string(object, "name");
            if (profileId != null && name != null && !name.isBlank()) {
                friends.add(new Friend(profileId, name));
            }
        }
        return List.copyOf(friends);
    }

    private static Map<UUID, Presence> parsePresence(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonArray presence = root.has("presence") && root.get("presence").isJsonArray()
            ? root.getAsJsonArray("presence")
            : new JsonArray();
        Map<UUID, Presence> statuses = new HashMap<>();
        for (JsonElement element : presence) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            UUID profileId = parseUuid(object, "profileId");
            if (profileId == null) {
                continue;
            }
            UUID presenceId = parseUuid(object, "pmid");
            String status = string(object, "status");
            JsonObject joinInfo = object.has("joinInfo") && object.get("joinInfo").isJsonObject()
                ? object.getAsJsonObject("joinInfo")
                : null;
            String joinValue = joinInfo != null ? string(joinInfo, "value") : null;
            statuses.put(profileId, new Presence(
                presenceId != null ? presenceId.toString() : null,
                officialStatus(status != null ? status : "OFFLINE"),
                joinValue != null && !joinValue.isBlank()
            ));
        }
        return Map.copyOf(statuses);
    }

    private static List<LinkFriendEntry> entries(List<Friend> friends, LinkFriendRelationship relationship, Map<UUID, Presence> presenceByProfile) {
        return friends.stream().map(friend -> {
            Presence presence = presenceByProfile.get(friend.profileId());
            List<LinkPresence> presences = presence == null || presence.presenceId() == null
                ? List.of()
                : List.of(new LinkPresence(
                    presence.presenceId(),
                    presence.status(),
                    presence.joinable(),
                    friend.name(),
                    null,
                    null,
                    null,
                    null
                ));
            return new LinkFriendEntry(friend.profileId(), friend.name(), relationship, presences);
        }).toList();
    }

    private static LinkPresenceStatus officialStatus(String status) {
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "PLAYING_HOSTED_SERVER" -> LinkPresenceStatus.HOSTING;
            case "PLAYING_REALMS" -> LinkPresenceStatus.PLAYING_REALMS;
            case "PLAYING_SERVER" -> LinkPresenceStatus.PLAYING_SERVER;
            case "PLAYING_OFFLINE" -> LinkPresenceStatus.PLAYING_OFFLINE;
            case "ONLINE" -> LinkPresenceStatus.ONLINE;
            case "OFFLINE" -> LinkPresenceStatus.OFFLINE;
            default -> LinkPresenceStatus.UNKNOWN;
        };
    }

    private static LinkFriendActionResult handleHttpStatus(int status) {
        if (status >= 200 && status < 300) {
            return LinkFriendActionResult.SUCCESS;
        }
        if (status == 400) {
            return LinkFriendActionResult.UNKNOWN_PROFILE;
        }
        if (status == 403) {
            return LinkFriendActionResult.FORBIDDEN;
        }
        if (status == 429) {
            return LinkFriendActionResult.TOO_MANY_REQUESTS;
        }
        if (status >= 500) {
            return LinkFriendActionResult.SERVICE_NOT_AVAILABLE;
        }
        return LinkFriendActionResult.ERROR;
    }

    private static @Nullable LinkFailure failureFor(LinkFriendActionResult result) {
        return switch (result) {
            case SUCCESS -> null;
            case SERVICE_NOT_AVAILABLE -> new LinkFailure(LinkFailureCode.SERVICE_UNAVAILABLE, "Friend service is unavailable", true);
            case TOO_MANY_REQUESTS -> new LinkFailure(LinkFailureCode.RATE_LIMITED, "Friend action rate limit exceeded", true);
            case FORBIDDEN -> new LinkFailure(LinkFailureCode.FORBIDDEN, "Friend action is forbidden", false);
            case UNKNOWN_PROFILE -> new LinkFailure(LinkFailureCode.PROFILE_NOT_FOUND, "Minecraft profile was not found", false);
            case ERROR -> new LinkFailure(LinkFailureCode.UNKNOWN, "Friend action failed", false);
        };
    }

    private static @Nullable String string(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            return null;
        }
        return object.get(key).getAsString();
    }

    private static @Nullable UUID parseUuid(JsonObject object, String key) {
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
            try {
                return UUID.fromString(compact.replaceFirst(
                    "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                    "$1-$2-$3-$4-$5"
                ).toLowerCase(Locale.ROOT));
            } catch (IllegalArgumentException ignoredAgain) {
                return null;
            }
        }
    }

    private record FriendLists(List<Friend> friends, List<Friend> incoming, List<Friend> outgoing) {
        private LinkFriendSnapshot snapshot(Map<UUID, Presence> presenceByProfile) {
            return new LinkFriendSnapshot(
                entries(this.friends, LinkFriendRelationship.FRIEND, presenceByProfile),
                entries(this.incoming, LinkFriendRelationship.INCOMING, presenceByProfile),
                entries(this.outgoing, LinkFriendRelationship.OUTGOING, presenceByProfile)
            );
        }
    }

    private record Friend(UUID profileId, String name) {
    }

    private record Presence(@Nullable String presenceId, LinkPresenceStatus status, boolean joinable) {
    }
}
