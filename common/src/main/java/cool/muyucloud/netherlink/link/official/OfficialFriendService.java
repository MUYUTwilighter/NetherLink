package cool.muyucloud.netherlink.link.official;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.account.MinecraftAccount;
import cool.muyucloud.netherlink.account.NetherLinkAuthException;
import cool.muyucloud.netherlink.http.JsonHttp;
import cool.muyucloud.netherlink.link.exception.LinkException;
import cool.muyucloud.netherlink.link.exception.LinkUnauthorizedException;
import cool.muyucloud.netherlink.link.model.*;
import cool.muyucloud.netherlink.link.service.LinkFriendService;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class OfficialFriendService implements LinkFriendService {
    private static final URI FRIENDS_URI = URI.create("https://api.minecraftservices.com/friends");
    private static final URI ATTRIBUTES_URI = URI.create("https://api.minecraftservices.com/player/attributes");
    private static final URI PRESENCE_URI = URI.create("https://api.minecraftservices.com/presence");
    private static final Duration FRIEND_REQUEST_COOLDOWN = Duration.ofSeconds(10L);
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "NetherLink Friends");
        thread.setDaemon(true);
        return thread;
    });

    private final HttpClient http = HttpClient.newHttpClient();
    private final String accessToken;
    private @Nullable String friendsEtag;
    private @Nullable String presenceEtag;
    private @Nullable Instant nextFriendRequest;
    private FriendLists friendCache = new FriendLists(List.of(), List.of(), List.of());
    private Map<UUID, Presence> presenceCache = Map.of();
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
    public CompletableFuture<LinkFriendSettings> settings() {
        return CompletableFuture.supplyAsync(this::getSettings, EXECUTOR);
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

    @Override
    public CompletableFuture<LinkFriendSettings> updateSettings(LinkFriendSettings settings) {
        return CompletableFuture.supplyAsync(() -> this.putSettings(settings), EXECUTOR);
    }

    private FriendLists getFriends() {
        if (this.nextFriendRequest != null && Instant.now().isBefore(this.nextFriendRequest)) {
            return this.friendCache;
        }
        this.nextFriendRequest = Instant.now().plus(FRIEND_REQUEST_COOLDOWN);
        HttpRequest.Builder builder = this.authorized(FRIENDS_URI).GET();
        if (this.friendsEtag != null) {
            builder.header("If-None-Match", this.friendsEtag);
        }
        try {
            HttpResponse<String> response = JsonHttp.sendString(this.http, builder.build());
            if (response.statusCode() == 304) {
                this.updateFriendPollInterval(response);
                return this.friendCache;
            }
            if (!JsonHttp.isSuccess(response.statusCode())) {
                throw requestFailure("Friend list request", response.statusCode());
            }
            this.updateFriendPollInterval(response);
            this.friendsEtag = response.headers().firstValue("ETag").orElse(null);
            return parseFriendLists(response.body());
        } catch (IOException e) {
            throw networkFailure("Friend list request failed", e);
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
            .POST(JsonHttp.jsonBody(body));
        if (this.presenceEtag != null) {
            builder.header("If-None-Match", this.presenceEtag);
        }
        try {
            HttpResponse<String> response = JsonHttp.sendString(this.http, builder.build());
            if (response.statusCode() == 304) {
                return this.presenceCache;
            }
            if (response.statusCode() == 401) {
                throw new LinkUnauthorizedException("Friend presence request failed: HTTP 401");
            }
            if (!JsonHttp.isSuccess(response.statusCode())) {
                NliConstants.LOG.warn("Friend presence request failed: HTTP {}", response.statusCode());
                return this.presenceCache;
            }
            this.presenceEtag = response.headers().firstValue("ETag").orElse(null);
            this.presenceCache = parsePresence(response.body());
            return this.presenceCache;
        } catch (IOException e) {
            NliConstants.LOG.warn("Friend presence request failed: {}", e.toString());
            return this.presenceCache;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return this.presenceCache;
        }
    }

    private LinkFriendSettings getSettings() {
        HttpRequest request = this.authorized(ATTRIBUTES_URI).GET().build();
        try {
            HttpResponse<String> response = JsonHttp.sendString(this.http, request);
            if (!JsonHttp.isSuccess(response.statusCode())) {
                throw requestFailure("Friend settings request", response.statusCode());
            }
            return parseSettings(response.body());
        } catch (IOException error) {
            throw networkFailure("Friend settings request failed", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new LinkException(new LinkFailure(LinkFailureCode.CANCELLED, "Friend settings request interrupted", true), error);
        }
    }

    private LinkFriendSettings putSettings(LinkFriendSettings settings) {
        JsonObject preferences = new JsonObject();
        preferences.addProperty("friends", settings.friendsEnabled() ? "ENABLED" : "DISABLED");
        preferences.addProperty("acceptInvites", settings.acceptInvites() ? "ENABLED" : "DISABLED");
        JsonObject body = new JsonObject();
        body.add("friendsPreferences", preferences);
        HttpRequest request = this.authorized(ATTRIBUTES_URI)
            .header("Content-Type", "application/json")
            .POST(JsonHttp.jsonBody(body))
            .build();
        try {
            HttpResponse<String> response = JsonHttp.sendString(this.http, request);
            if (!JsonHttp.isSuccess(response.statusCode())) {
                throw requestFailure("Friend settings update", response.statusCode());
            }
            return settings;
        } catch (IOException error) {
            throw networkFailure("Friend settings update failed", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new LinkException(new LinkFailure(LinkFailureCode.CANCELLED, "Friend settings update interrupted", true), error);
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
            .PUT(JsonHttp.jsonBody(body))
            .build();
        try {
            HttpResponse<String> response = JsonHttp.sendString(this.http, request);
            LinkFriendActionResult result = handleHttpStatus(response.statusCode());
            if (response.statusCode() == 401) {
                throw new LinkUnauthorizedException("Friend action failed: HTTP 401");
            }
            if (result == LinkFriendActionResult.SUCCESS) {
                this.nextFriendRequest = Instant.now().plus(FRIEND_REQUEST_COOLDOWN);
                this.friendsEtag = null;
                if (response.body() != null && !response.body().isBlank()) {
                    this.friendCache = parseFriendLists(response.body());
                }
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
        JsonObject root = JsonHttp.parseObject(body);
        return new FriendLists(
            parseFriends(root, "friends"),
            parseFriends(root, "incomingRequests"),
            parseFriends(root, "outgoingRequests")
        );
    }

    private static List<Friend> parseFriends(JsonObject root, String key) {
        List<Friend> friends = new ArrayList<>();
        for (JsonElement element : JsonHttp.array(root, key)) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            UUID profileId = JsonHttp.uuidLenient(object, "profileId");
            String name = JsonHttp.string(object, "name");
            if (profileId != null && name != null && !name.isBlank()) {
                friends.add(new Friend(profileId, name));
            }
        }
        return List.copyOf(friends);
    }

    private static Map<UUID, Presence> parsePresence(String body) {
        JsonObject root = JsonHttp.parseObject(body);
        JsonArray presence = JsonHttp.array(root, "presence");
        Map<UUID, Presence> statuses = new HashMap<>();
        for (JsonElement element : presence) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            UUID profileId = JsonHttp.uuidLenient(object, "profileId");
            if (profileId == null) {
                continue;
            }
            UUID presenceId = JsonHttp.uuidLenient(object, "pmid");
            String status = JsonHttp.string(object, "status");
            Instant lastUpdated = JsonHttp.instant(object, "lastUpdated");
            LinkPresenceStatus mappedStatus = officialStatus(status != null ? status : "OFFLINE");
            statuses.put(profileId, new Presence(
                presenceId != null ? presenceId.toString() : null,
                mappedStatus,
                presenceId != null && mappedStatus == LinkPresenceStatus.HOSTING,
                lastUpdated
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
                    presence.lastUpdated(),
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
        if (JsonHttp.isSuccess(status)) {
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

    private void updateFriendPollInterval(HttpResponse<?> response) {
        JsonHttp.retryAfter(response).ifPresent(duration -> this.nextFriendRequest = Instant.now().plus(duration));
    }

    private static LinkFriendSettings parseSettings(String body) {
        JsonObject root = JsonHttp.parseObject(body);
        JsonObject preferences = JsonHttp.object(root, "friendsPreferences");
        return new LinkFriendSettings(
            "ENABLED".equalsIgnoreCase(JsonHttp.string(preferences, "friends")),
            "ENABLED".equalsIgnoreCase(JsonHttp.string(preferences, "acceptInvites"))
        );
    }

    private static RuntimeException requestFailure(String operation, int status) {
        if (status == 401) {
            return new LinkUnauthorizedException(operation + " failed: HTTP 401");
        }
        LinkFailureCode code = status == 403 ? LinkFailureCode.FORBIDDEN
            : status == 429 ? LinkFailureCode.RATE_LIMITED
            : status >= 500 ? LinkFailureCode.SERVICE_UNAVAILABLE
            : LinkFailureCode.UNKNOWN;
        return new LinkException(new LinkFailure(code, operation + " failed: HTTP " + status, status == 429 || status >= 500));
    }

    private static LinkException networkFailure(String message, IOException error) {
        return new LinkException(new LinkFailure(LinkFailureCode.NETWORK, message, true), error);
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

    private record Presence(@Nullable String presenceId, LinkPresenceStatus status, boolean joinable, @Nullable Instant lastUpdated) {
    }
}
