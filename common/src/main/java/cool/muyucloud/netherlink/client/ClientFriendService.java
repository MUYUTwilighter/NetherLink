package cool.muyucloud.netherlink.client;

import cool.muyucloud.netherlink.link.LinkServices;
import cool.muyucloud.netherlink.link.hook.LinkContextHooks;
import cool.muyucloud.netherlink.link.model.*;
import cool.muyucloud.netherlink.link.service.LinkFriendService;
import cool.muyucloud.netherlink.link.service.LinkRuntimeService;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ClientFriendService {
    private final LinkFriendService backend;
    private final CompletableFuture<?> runtimeReady;

    public ClientFriendService(Minecraft minecraft) {
        LauncherSessionAccount account = new LauncherSessionAccount(minecraft.getUser());
        LinkContextHooks.setClientConnection(
            LinkRuntimeService.CLIENT_KEY,
            account,
            "Minecraft Java instance",
            new MinecraftClientConnectionBridge(minecraft)
        );
        this.runtimeReady = LinkServices.current().runtime().open(LinkRuntimeService.CLIENT_KEY);
        this.backend = LinkServices.current().createFriendService(LinkRuntimeService.CLIENT_KEY);
    }

    public CompletableFuture<Snapshot> refresh() {
        return this.runtimeReady.thenCompose(ignored1 -> this.backend.refresh()).thenApply(ClientFriendService::snapshot);
    }

    public CompletableFuture<LinkFriendSettings> settings() {
        return this.runtimeReady.thenCompose(ignored1 -> this.backend.settings());
    }

    public CompletableFuture<LinkFriendActionOutcome> add(String name) {
        return this.runtimeReady.thenCompose(ignored1 -> this.backend.add(name));
    }

    public CompletableFuture<LinkFriendActionOutcome> remove(UUID profileId) {
        return this.runtimeReady.thenCompose(ignored1 -> this.backend.remove(profileId));
    }

    public CompletableFuture<LinkFriendActionOutcome> accept(UUID profileId) {
        return this.runtimeReady.thenCompose(ignored1 -> this.backend.accept(profileId));
    }

    public CompletableFuture<LinkFriendActionOutcome> decline(UUID profileId) {
        return this.runtimeReady.thenCompose(ignored1 -> this.backend.decline(profileId));
    }

    public CompletableFuture<LinkFriendActionOutcome> revoke(UUID profileId) {
        return this.runtimeReady.thenCompose(ignored1 -> this.backend.revoke(profileId));
    }

    public CompletableFuture<LinkFriendSettings> updateSettings(LinkFriendSettings settings) {
        return this.runtimeReady.thenCompose(ignored1 -> this.backend.updateSettings(settings));
    }

    private static Snapshot snapshot(LinkFriendSnapshot snapshot) {
        return new Snapshot(
            friends(snapshot.friends()),
            requests(snapshot.incoming()),
            requests(snapshot.outgoing())
        );
    }

    private static List<Friend> friends(List<LinkFriendEntry> entries) {
        return entries.stream()
            .map(entry -> new Friend(
                entry.profileId(),
                displayName(entry),
                entry.presences().stream().map(ClientFriendService::instance).sorted(INSTANCE_ORDER).toList()
            ))
            .sorted(FRIEND_ORDER)
            .toList();
    }

    private static List<Request> requests(List<LinkFriendEntry> entries) {
        return entries.stream()
            .map(entry -> new Request(entry.profileId(), displayName(entry), entry.relationship()))
            .sorted(Comparator.comparing(Request::name, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    private static String displayName(LinkFriendEntry entry) {
        return entry.name() != null && !entry.name().isBlank() ? entry.name() : entry.profileId().toString();
    }

    private static Instance instance(LinkPresence presence) {
        return new Instance(
            presence.presenceId(),
            presence.status(),
            presence.joinable(),
            presence.displayText()
        );
    }

    public record Snapshot(List<Friend> friends, List<Request> incoming, List<Request> outgoing) {
        public Snapshot {
            friends = List.copyOf(friends);
            incoming = List.copyOf(incoming);
            outgoing = List.copyOf(outgoing);
        }
    }

    public record Friend(UUID profileId, String name, List<Instance> instances) {
        public Friend {
            instances = List.copyOf(instances);
        }
    }

    public record Instance(@Nullable String presenceId, LinkPresenceStatus status, boolean joinable, String displayText) {
    }

    public record Request(UUID profileId, String name, LinkFriendRelationship relationship) {
    }

    private static final Comparator<Friend> FRIEND_ORDER = Comparator
        .comparingInt(ClientFriendService::friendRank)
        .thenComparing(Friend::name, String.CASE_INSENSITIVE_ORDER)
        .thenComparing(Friend::name)
        .thenComparing(Friend::profileId);

    private static final Comparator<Instance> INSTANCE_ORDER = Comparator
        .comparingInt((Instance instance) -> presenceRank(instance.status()))
        .thenComparing(instance -> instance.presenceId() != null ? instance.presenceId() : "");

    private static int friendRank(Friend friend) {
        if (friend.instances().stream().anyMatch(Instance::joinable)) {
            return 0;
        }
        return friend.instances().isEmpty() ? 2 : 1;
    }

    private static int presenceRank(LinkPresenceStatus status) {
        return switch (status) {
            case HOSTING -> 0;
            case PLAYING_REALMS -> 1;
            case PLAYING_SERVER -> 2;
            case PLAYING_OFFLINE -> 3;
            case ONLINE -> 4;
            case OFFLINE -> 5;
            case UNKNOWN -> 6;
        };
    }

}
