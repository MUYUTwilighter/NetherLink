package cool.muyucloud.netherlink.link;

import cool.muyucloud.netherlink.link.official.OfficialLinkServiceProvider;

import java.util.concurrent.CompletableFuture;

/** Global backend selection point used by game-facing code. */
public final class LinkServices {
    private static volatile LinkService service = OfficialLinkServiceProvider.INSTANCE;
    private static volatile CompletableFuture<Void> transition = CompletableFuture.completedFuture(null);

    private LinkServices() {
    }

    /**
     * Returns the active service.
     *
     * @throws IllegalStateException while an asynchronous backend transition is in progress
     */
    public static LinkService current() {
        if (!transition.isDone()) {
            throw new IllegalStateException("LinkService is switching backends");
        }
        return service;
    }

    /**
     * Shuts down the active backend and installs {@code nextService} after cleanup succeeds.
     * Concurrent transitions are rejected; callers should wait for the returned future before
     * starting operations on the new backend.
     */
    @SuppressWarnings("unused")
    public static synchronized CompletableFuture<Void> use(LinkService nextService) {
        LinkService previous = service;
        if (previous == nextService) {
            return transition;
        }
        if (!transition.isDone()) {
            return CompletableFuture.failedFuture(new IllegalStateException("A LinkService transition is already in progress"));
        }
        transition = previous.shutdown().thenRun(() -> service = nextService);
        return transition;
    }
}
