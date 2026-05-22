package cool.muyucloud.netherlink.client;

import com.mojang.authlib.yggdrasil.FriendsService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.authlib.yggdrasil.request.JoinInfoUpdate;
import com.mojang.authlib.yggdrasil.response.FriendData;
import com.mojang.authlib.yggdrasil.response.FriendDto;
import com.mojang.authlib.yggdrasil.response.PresenceResponse;
import com.mojang.authlib.yggdrasil.response.PresenceStatus;
import com.mojang.authlib.yggdrasil.response.PresenceStatusDto;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientFriendService {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "NetherLink Friends");
        thread.setDaemon(true);
        return thread;
    });
    private final FriendsService service;

    public ClientFriendService(Minecraft minecraft) {
        this.service = new YggdrasilAuthenticationService(minecraft.getProxy()).createFriendsService(minecraft.getUser().getAccessToken());
    }

    public CompletableFuture<Snapshot> refresh() {
        return CompletableFuture.supplyAsync(() -> {
            Holder holder = new Holder();
            FriendsService.ResultCode friendsResult = this.service.getFriendData(data -> holder.data = data);
            if (friendsResult != FriendsService.ResultCode.SUCCESS) {
                throw new IllegalStateException("Friend list request failed: " + friendsResult);
            }
            FriendData data = holder.data != null ? holder.data : FriendData.empty();
            Map<UUID, PresenceStatusDto> presenceByProfile = new ConcurrentHashMap<>();
            PresenceResponse presence = this.service.presence(PresenceStatus.ONLINE.name(), new JoinInfoUpdate(null, Set.of()));
            presence.presence().forEach(status -> presenceByProfile.put(status.profileId(), status));

            List<Entry> friends = data.friends().stream().map(friend -> entry(friend, Relationship.FRIEND, presenceByProfile.get(friend.profileId()))).toList();
            List<Entry> incoming = data.incomingRequests().stream().map(friend -> entry(friend, Relationship.INCOMING, presenceByProfile.get(friend.profileId()))).toList();
            List<Entry> outgoing = data.outgoingRequests().stream().map(friend -> entry(friend, Relationship.OUTGOING, presenceByProfile.get(friend.profileId()))).toList();
            return new Snapshot(new ArrayList<>(friends), new ArrayList<>(incoming), new ArrayList<>(outgoing));
        }, EXECUTOR);
    }

    public CompletableFuture<FriendsService.ResultCode> add(String name) {
        return CompletableFuture.supplyAsync(() -> this.service.sendFriendRequest(name), EXECUTOR);
    }

    public CompletableFuture<FriendsService.ResultCode> remove(UUID profileId) {
        return CompletableFuture.supplyAsync(() -> this.service.removeFriend(profileId), EXECUTOR);
    }

    public CompletableFuture<FriendsService.ResultCode> accept(UUID profileId) {
        return CompletableFuture.supplyAsync(() -> this.service.acceptIncomingFriendRequest(profileId), EXECUTOR);
    }

    public CompletableFuture<FriendsService.ResultCode> decline(UUID profileId) {
        return CompletableFuture.supplyAsync(() -> this.service.declineIncomingFriendRequest(profileId), EXECUTOR);
    }

    public CompletableFuture<FriendsService.ResultCode> revoke(UUID profileId) {
        return CompletableFuture.supplyAsync(() -> this.service.revokeOutgoingFriendRequest(profileId), EXECUTOR);
    }

    private static Entry entry(FriendDto friend, Relationship relationship, PresenceStatusDto presence) {
        UUID pmid = presence != null ? presence.pmid() : null;
        PresenceStatus status = presence != null ? presence.status() : PresenceStatus.OFFLINE;
        boolean joinable = presence != null && presence.joinInfo() != null && presence.joinInfo().value() != null && !presence.joinInfo().value().isBlank();
        return new Entry(friend.profileId(), friend.name(), pmid, relationship, status, joinable);
    }

    private static final class Holder {
        private FriendData data;
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

    public record Entry(UUID profileId, String name, UUID pmid, Relationship relationship, PresenceStatus status, boolean joinable) {
    }

    public enum Relationship {
        FRIEND,
        INCOMING,
        OUTGOING
    }
}
