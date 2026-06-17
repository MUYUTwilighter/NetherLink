package cool.muyucloud.netherlink.client;

import cool.muyucloud.netherlink.link.LinkServices;
import cool.muyucloud.netherlink.link.hook.LinkClientHooks;
import cool.muyucloud.netherlink.link.model.LinkFriendActionResult;
import cool.muyucloud.netherlink.link.model.LinkFriendEntry;
import cool.muyucloud.netherlink.link.model.LinkFriendRelationship;
import cool.muyucloud.netherlink.link.model.LinkFriendSnapshot;
import cool.muyucloud.netherlink.link.service.LinkFriendService;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ClientFriendService {
    private final LinkFriendService backend;

    public ClientFriendService(Minecraft minecraft) {
        LauncherSessionAccount account = new LauncherSessionAccount(minecraft.getUser());
        LinkClientHooks.setClient(minecraft, account);
        this.backend = LinkServices.current().createFriendService();
    }

    public CompletableFuture<Snapshot> refresh() {
        return this.backend.refresh().thenApply(ClientFriendService::snapshot);
    }

    public CompletableFuture<ResultCode> add(String name) {
        return this.backend.add(name).thenApply(ClientFriendService::resultCode);
    }

    public CompletableFuture<ResultCode> remove(UUID profileId) {
        return this.backend.remove(profileId).thenApply(ClientFriendService::resultCode);
    }

    public CompletableFuture<ResultCode> accept(UUID profileId) {
        return this.backend.accept(profileId).thenApply(ClientFriendService::resultCode);
    }

    public CompletableFuture<ResultCode> decline(UUID profileId) {
        return this.backend.decline(profileId).thenApply(ClientFriendService::resultCode);
    }

    public CompletableFuture<ResultCode> revoke(UUID profileId) {
        return this.backend.revoke(profileId).thenApply(ClientFriendService::resultCode);
    }

    private static Snapshot snapshot(LinkFriendSnapshot snapshot) {
        return new Snapshot(
            entries(snapshot.friends()),
            entries(snapshot.incoming()),
            entries(snapshot.outgoing())
        );
    }

    private static List<Entry> entries(List<LinkFriendEntry> entries) {
        return entries.stream()
            .map(entry -> new Entry(entry.profileId(), entry.name(), entry.presenceId(), relationship(entry.relationship()), entry.status(), entry.joinable()))
            .toList();
    }

    private static Relationship relationship(LinkFriendRelationship relationship) {
        return switch (relationship) {
            case FRIEND -> Relationship.FRIEND;
            case INCOMING -> Relationship.INCOMING;
            case OUTGOING -> Relationship.OUTGOING;
        };
    }

    private static ResultCode resultCode(LinkFriendActionResult result) {
        return switch (result) {
            case SUCCESS -> ResultCode.SUCCESS;
            case ERROR -> ResultCode.ERROR;
            case SERVICE_NOT_AVAILABLE -> ResultCode.SERVICE_NOT_AVAILABLE;
            case TOO_MANY_REQUESTS -> ResultCode.TOO_MANY_REQUESTS;
            case FORBIDDEN -> ResultCode.FORBIDDEN;
            case UNKNOWN_PROFILE -> ResultCode.UNKNOWN_PROFILE;
        };
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
