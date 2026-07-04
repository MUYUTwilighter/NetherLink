package cool.muyucloud.netherlink.link.official;

import cool.muyucloud.netherlink.account.MinecraftAccount;
import cool.muyucloud.netherlink.account.NetherLinkAuthException;
import cool.muyucloud.netherlink.link.hook.LinkContextHooks;
import cool.muyucloud.netherlink.link.model.LinkRuntimeIdentity;
import cool.muyucloud.netherlink.link.model.LinkRuntimeSnapshot;
import cool.muyucloud.netherlink.link.model.LinkRuntimeState;
import cool.muyucloud.netherlink.link.service.LinkRuntimeService;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class OfficialRuntimeService implements LinkRuntimeService {
    private final ConcurrentHashMap<String, LinkRuntimeIdentity> runtimes = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<LinkRuntimeIdentity> open(String key) {
        try {
            LinkRuntimeIdentity existing = this.runtimes.get(key);
            if (existing != null) {
                return CompletableFuture.completedFuture(existing);
            }
            MinecraftAccount account = LinkContextHooks.require(key).account();
            String profileId = account.getMcProfileId();
            String name = account.getMcProfileName();
            if (profileId == null || name == null || name.isBlank()) {
                throw new NetherLinkAuthException("Minecraft profile identity was not found");
            }
            LinkRuntimeIdentity identity = new LinkRuntimeIdentity(parseProfileId(profileId), name, null, null);
            LinkRuntimeIdentity raced = this.runtimes.putIfAbsent(key, identity);
            return CompletableFuture.completedFuture(raced != null ? raced : identity);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public CompletableFuture<LinkRuntimeIdentity> renew(String key) {
        LinkRuntimeIdentity identity = this.runtimes.get(key);
        return identity != null
            ? CompletableFuture.completedFuture(identity)
            : CompletableFuture.failedFuture(new IllegalStateException("Official runtime is not open"));
    }

    @Override
    public CompletableFuture<Void> close(String key) {
        this.runtimes.remove(key);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> closeAll() {
        this.runtimes.clear();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public @Nullable LinkRuntimeIdentity current(String key) {
        return this.runtimes.get(key);
    }

    @Override
    public LinkRuntimeSnapshot snapshot(String key) {
        LinkRuntimeIdentity identity = this.runtimes.get(key);
        return new LinkRuntimeSnapshot(
            key,
            identity != null ? LinkRuntimeState.ACTIVE : LinkRuntimeState.CLOSED,
            identity,
            null
        );
    }

    private static UUID parseProfileId(String value) {
        String normalized = value;
        if (value.length() == 32) {
            normalized = value.substring(0, 8) + "-" + value.substring(8, 12) + "-" + value.substring(12, 16)
                + "-" + value.substring(16, 20) + "-" + value.substring(20);
        }
        return UUID.fromString(normalized);
    }
}
