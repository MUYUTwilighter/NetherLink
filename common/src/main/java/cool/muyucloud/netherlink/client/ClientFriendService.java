package cool.muyucloud.netherlink.client;

import cool.muyucloud.netherlink.link.LinkServices;
import cool.muyucloud.netherlink.link.hook.LinkContextHooks;
import cool.muyucloud.netherlink.link.model.LinkFriendActionOutcome;
import cool.muyucloud.netherlink.link.model.LinkFriendEntry;
import cool.muyucloud.netherlink.link.model.LinkFriendRelationship;
import cool.muyucloud.netherlink.link.model.LinkFriendSnapshot;
import cool.muyucloud.netherlink.link.model.LinkPresence;
import cool.muyucloud.netherlink.link.model.LinkPresenceStatus;
import cool.muyucloud.netherlink.link.service.LinkFriendService;
import cool.muyucloud.netherlink.link.service.LinkRuntimeService;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
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
        return this.runtimeReady.thenCompose(_ -> this.backend.refresh()).thenApply(ClientFriendService::snapshot);
    }

    public CompletableFuture<LinkFriendActionOutcome> add(String name) {
        return this.runtimeReady.thenCompose(_ -> this.backend.add(name));
    }

    public CompletableFuture<LinkFriendActionOutcome> remove(UUID profileId) {
        return this.runtimeReady.thenCompose(_ -> this.backend.remove(profileId));
    }

    public CompletableFuture<LinkFriendActionOutcome> accept(UUID profileId) {
        return this.runtimeReady.thenCompose(_ -> this.backend.accept(profileId));
    }

    public CompletableFuture<LinkFriendActionOutcome> decline(UUID profileId) {
        return this.runtimeReady.thenCompose(_ -> this.backend.decline(profileId));
    }

    public CompletableFuture<LinkFriendActionOutcome> revoke(UUID profileId) {
        return this.runtimeReady.thenCompose(_ -> this.backend.revoke(profileId));
    }

    private static Snapshot snapshot(LinkFriendSnapshot snapshot) {
        return new Snapshot(
            entries(snapshot.friends()),
            entries(snapshot.incoming()),
            entries(snapshot.outgoing())
        );
    }

    private static List<Entry> entries(List<LinkFriendEntry> entries) {
        List<Entry> result = new ArrayList<>();
        for (LinkFriendEntry entry : entries) {
            LinkFriendRelationship relationship = entry.relationship();
            String displayName = entry.name() != null && !entry.name().isBlank() ? entry.name() : entry.profileId().toString();
            if (entry.presences().isEmpty()) {
                result.add(new Entry(entry.profileId(), displayName, null, relationship, LinkPresenceStatus.OFFLINE, false, ""));
                continue;
            }
            for (LinkPresence presence : entry.presences()) {
                result.add(new Entry(
                    entry.profileId(),
                    displayName,
                    presence.presenceId(),
                    relationship,
                    presence.status(),
                    presence.joinable(),
                    presence.displayText()
                ));
            }
        }
        result.sort(ENTRY_ORDER);
        return List.copyOf(result);
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

    public record Entry(UUID profileId, String name, @Nullable String presenceId, LinkFriendRelationship relationship, LinkPresenceStatus status, boolean joinable, String displayText) {
    }

    private static final Comparator<Entry> ENTRY_ORDER = Comparator.<Entry>comparingInt(entry -> relationshipRank(entry.relationship()))
        .thenComparingInt(entry -> presenceRank(entry.status()))
        .thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER)
        .thenComparing(entry -> entry.presenceId() != null ? entry.presenceId() : "");

    private static int relationshipRank(LinkFriendRelationship relationship) {
        return switch (relationship) {
            case INCOMING -> 0;
            case OUTGOING -> 1;
            case FRIEND -> 2;
        };
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
