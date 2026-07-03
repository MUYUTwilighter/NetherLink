package cool.muyucloud.netherlink.client;

import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.link.LinkService;
import cool.muyucloud.netherlink.link.LinkServices;
import cool.muyucloud.netherlink.link.hook.LinkContextHooks;
import cool.muyucloud.netherlink.link.model.LinkFriendSettings;
import cool.muyucloud.netherlink.link.model.LinkTerms;
import cool.muyucloud.netherlink.link.model.LinkTermsState;
import cool.muyucloud.netherlink.link.service.LinkRuntimeService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ClientTermsController {
    private static final ConcurrentMap<CacheKey, CompletableFuture<LinkTermsState>> CACHE = new ConcurrentHashMap<>();

    private ClientTermsController() {
    }

    /** Silently checks the configured backend's terms once the client is ready. */
    public static void prefetch(Minecraft minecraft) {
        minecraft.execute(() -> {
            try {
                ClientLinkSettings.applyConfiguredService(minecraft);
                LinkService service = LinkServices.current();
                termsState(minecraft, service).thenAccept(state -> {
                    if (state.status().needsPrompt()) {
                        NliConstants.LOG.debug("Prefetched terms status for {}: {}", service.id(), state.status());
                        return;
                    }
                    publishOnline(minecraft, service);
                });
            } catch (RuntimeException error) {
                NliConstants.LOG.warn("Failed to start silent NetherLink terms prefetch", error);
            }
        });
    }

    /** Runs an action only after the active backend's current terms have been accepted. */
    public static void runAfterAcceptance(Minecraft minecraft, Screen parent, Runnable action) {
        minecraft.execute(() -> {
            ClientLinkSettings.applyConfiguredService(minecraft);
            LinkService service = LinkServices.current();
            runAfterAcceptance(minecraft, parent, service, action);
        });
    }

    /** Runs an action only after the supplied backend's current terms have been accepted. */
    public static void runAfterAcceptance(Minecraft minecraft, Screen parent, LinkService service, Runnable action) {
        minecraft.execute(() -> {
            CompletableFuture<LinkTermsState> terms = termsState(minecraft, service);
            if (!terms.isDone()) {
                minecraft.setScreen(new NetherLinkTermsScreen(parent, service, action));
                return;
            }
            terms.whenComplete((state, failure) -> minecraft.execute(() -> {
                LinkTermsState resolved = failure == null ? state : LinkTermsState.error(failure);
                if (resolved.isAccepted()) {
                    action.run();
                    return;
                }
                minecraft.setScreen(new NetherLinkTermsScreen(parent, service, action, resolved));
            }));
        });
    }

    static CompletableFuture<LinkTermsState> termsState(Minecraft minecraft, LinkService service) {
        return CACHE.computeIfAbsent(cacheKey(minecraft, service), ignored -> requestTermsState(minecraft, service));
    }

    static CompletableFuture<LinkTermsState> refreshTermsState(Minecraft minecraft, LinkService service) {
        CacheKey key = cacheKey(minecraft, service);
        CompletableFuture<LinkTermsState> refreshed = requestTermsState(minecraft, service);
        CACHE.put(key, refreshed);
        return refreshed;
    }

    static CompletableFuture<LinkFriendSettings> friendSettings(Minecraft minecraft, LinkService service) {
        if (!service.supports(LinkService.Capability.FRIEND_SETTINGS)) {
            return CompletableFuture.completedFuture(new LinkFriendSettings(true, true));
        }
        return new ClientFriendService(minecraft, service).settings();
    }

    static void markAccepted(Minecraft minecraft, LinkService service, LinkTerms terms) {
        CACHE.put(cacheKey(minecraft, service), CompletableFuture.completedFuture(LinkTermsState.accepted(terms)));
    }

    private static void publishOnline(Minecraft minecraft, LinkService service) {
        minecraft.execute(() -> {
            LauncherSessionAccount account = new LauncherSessionAccount(minecraft.getUser());
            if (!account.isUsable()) {
                NliConstants.LOG.debug("Skipping NetherLink startup online presence publish because the launcher account has no Minecraft access token");
                return;
            }
            LinkContextHooks.setClientConnection(
                LinkRuntimeService.CLIENT_KEY,
                account,
                NliConstants.resolveInstanceName(),
                new MinecraftClientConnectionBridge(minecraft)
            );
            service.runtime().publishOnline(LinkRuntimeService.CLIENT_KEY).exceptionally(error -> {
                NliConstants.LOG.warn("Failed to publish NetherLink startup online presence for {}", service.id(), error);
                return null;
            });
        });
    }

    private static CompletableFuture<LinkTermsState> requestTermsState(Minecraft minecraft, LinkService service) {
        String language = minecraft.getLanguageManager().getSelected().replace('_', '-');
        return service.termsStatus(language).handle((state, failure) -> {
            if (failure != null) {
                NliConstants.LOG.warn("Failed to check terms status for {}", service.id(), failure);
                return LinkTermsState.error(failure);
            }
            return state;
        });
    }

    private static CacheKey cacheKey(Minecraft minecraft, LinkService service) {
        String language = minecraft.getLanguageManager().getSelected().replace('_', '-');
        return new CacheKey(service.termsCacheScope(), language);
    }

    private record CacheKey(String serviceScope, String language) {
    }
}
