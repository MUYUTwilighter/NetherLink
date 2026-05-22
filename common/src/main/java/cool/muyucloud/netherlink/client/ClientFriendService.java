package cool.muyucloud.netherlink.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cool.muyucloud.netherlink.NliConstants;
import net.minecraft.client.Minecraft;
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

public final class ClientFriendService {
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
    private Snapshot cache = new Snapshot(List.of(), List.of(), List.of());

    public ClientFriendService(Minecraft minecraft) {
        this.accessToken = minecraft.getUser().getAccessToken();
    }

    public CompletableFuture<Snapshot> refresh() {
        return CompletableFuture.supplyAsync(() -> {
            FriendLists lists = this.getFriends();
            this.friendCache = lists;
            Map<UUID, Presence> presenceByProfile = this.getPresence();
            List<Entry> friends = entries(lists.friends(), Relationship.FRIEND, presenceByProfile);
            List<Entry> incoming = entries(lists.incoming(), Relationship.INCOMING, presenceByProfile);
            List<Entry> outgoing = entries(lists.outgoing(), Relationship.OUTGOING, presenceByProfile);
            this.cache = new Snapshot(friends, incoming, outgoing);
            return this.cache;
        }, EXECUTOR);
    }

    public CompletableFuture<ResultCode> add(String name) {
        return CompletableFuture.supplyAsync(() -> this.putFriendAction(name, null, "ADD"), EXECUTOR);
    }

    public CompletableFuture<ResultCode> remove(UUID profileId) {
        return CompletableFuture.supplyAsync(() -> this.putFriendAction(null, profileId, "REMOVE"), EXECUTOR);
    }

    public CompletableFuture<ResultCode> accept(UUID profileId) {
        return CompletableFuture.supplyAsync(() -> this.putFriendAction(null, profileId, "ADD"), EXECUTOR);
    }

    public CompletableFuture<ResultCode> decline(UUID profileId) {
        return CompletableFuture.supplyAsync(() -> this.putFriendAction(null, profileId, "REMOVE"), EXECUTOR);
    }

    public CompletableFuture<ResultCode> revoke(UUID profileId) {
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
                this.cache.all().forEach(entry -> cached.put(entry.profileId(), new Presence(entry.pmid(), entry.status(), entry.joinable())));
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

    private ResultCode putFriendAction(@Nullable String name, @Nullable UUID profileId, String updateType) {
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
            ResultCode result = handleHttpStatus(response.statusCode());
            if (result == ResultCode.SUCCESS) {
                this.friendsEtag = null;
                this.friendCache = parseFriendLists(response.body());
                this.cache = this.friendCache.snapshot(Map.of());
            }
            return result;
        } catch (IOException e) {
            NliConstants.LOG.warn("Friend action failed: {}", e.toString());
            return ResultCode.ERROR;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResultCode.ERROR;
        }
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
            UUID pmid = parseUuid(object, "pmid");
            String status = string(object, "status");
            JsonObject joinInfo = object.has("joinInfo") && object.get("joinInfo").isJsonObject()
                ? object.getAsJsonObject("joinInfo")
                : null;
            String joinValue = joinInfo != null ? string(joinInfo, "value") : null;
            statuses.put(profileId, new Presence(pmid, status != null ? status : "OFFLINE", joinValue != null && !joinValue.isBlank()));
        }
        return Map.copyOf(statuses);
    }

    private static List<Entry> entries(List<Friend> friends, Relationship relationship, Map<UUID, Presence> presenceByProfile) {
        return friends.stream().map(friend -> {
            Presence presence = presenceByProfile.get(friend.profileId());
            return new Entry(
                friend.profileId(),
                friend.name(),
                presence != null ? presence.pmid() : null,
                relationship,
                presence != null ? presence.status() : "OFFLINE",
                presence != null && presence.joinable()
            );
        }).sorted(ENTRY_ORDER).toList();
    }

    private static final Comparator<Entry> ENTRY_ORDER = Comparator.<Entry>comparingInt(entry -> relationshipRank(entry.relationship()))
        .thenComparingInt(entry -> presenceRank(entry.status()))
        .thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER)
        .thenComparing(entry -> entry.profileId().toString());

    private static int relationshipRank(Relationship relationship) {
        return switch (relationship) {
            case INCOMING -> 0;
            case OUTGOING -> 1;
            case FRIEND -> 2;
        };
    }

    private static int presenceRank(String status) {
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "PLAYING_HOSTED_SERVER" -> 0;
            case "PLAYING_REALMS" -> 1;
            case "PLAYING_SERVER" -> 2;
            case "PLAYING_OFFLINE" -> 3;
            case "ONLINE" -> 4;
            case "OFFLINE" -> 5;
            default -> 6;
        };
    }

    private static ResultCode handleHttpStatus(int status) {
        if (status >= 200 && status < 300) {
            return ResultCode.SUCCESS;
        }
        if (status == 400) {
            return ResultCode.UNKNOWN_PROFILE;
        }
        if (status == 403) {
            return ResultCode.FORBIDDEN;
        }
        if (status == 429) {
            return ResultCode.TOO_MANY_REQUESTS;
        }
        if (status >= 500) {
            return ResultCode.SERVICE_NOT_AVAILABLE;
        }
        return ResultCode.ERROR;
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
        private Snapshot snapshot(Map<UUID, Presence> presenceByProfile) {
            return new Snapshot(
                entries(this.friends, Relationship.FRIEND, presenceByProfile),
                entries(this.incoming, Relationship.INCOMING, presenceByProfile),
                entries(this.outgoing, Relationship.OUTGOING, presenceByProfile)
            );
        }
    }

    private record Friend(UUID profileId, String name) {
    }

    private record Presence(@Nullable UUID pmid, String status, boolean joinable) {
    }

    public record Snapshot(List<Entry> friends, List<Entry> incoming, List<Entry> outgoing) {
        public List<Entry> all() {
            List<Entry> entries = new ArrayList<>();
            entries.addAll(this.friends);
            entries.addAll(this.incoming);
            entries.addAll(this.outgoing);
            return entries;
        }
    }

    public record Entry(UUID profileId, String name, @Nullable UUID pmid, Relationship relationship, String status, boolean joinable) {
    }

    public enum Relationship {
        FRIEND,
        INCOMING,
        OUTGOING
    }

    public enum ResultCode {
        SUCCESS,
        ERROR,
        SERVICE_NOT_AVAILABLE,
        TOO_MANY_REQUESTS,
        FORBIDDEN,
        UNKNOWN_PROFILE
    }
}
