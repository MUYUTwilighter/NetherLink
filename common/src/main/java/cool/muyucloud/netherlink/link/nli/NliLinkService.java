package cool.muyucloud.netherlink.link.nli;

import cool.muyucloud.netherlink.link.LinkService;
import cool.muyucloud.netherlink.link.service.LinkFriendService;
import cool.muyucloud.netherlink.link.service.LinkHostingService;
import cool.muyucloud.netherlink.link.service.LinkJoinService;
import cool.muyucloud.netherlink.link.service.LinkRuntimeService;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class NliLinkService implements LinkService {
    public static final NliLinkService INSTANCE = new NliLinkService(defaultUri());
    private static final Set<Capability> CAPABILITIES = Set.of(
        Capability.FRIENDS,
        Capability.HOSTING,
        Capability.JOINING,
        Capability.MULTI_ACCOUNT_RUNTIME,
        Capability.MULTI_PRESENCE
    );

    private final NliApiClient api;
    private final NliRuntimeService runtime;
    private final NliHostingService hosting;
    private final NliJoinService joining;

    public NliLinkService(URI baseUri) {
        this.api = new NliApiClient(baseUri);
        this.runtime = new NliRuntimeService(this.api);
        this.hosting = new NliHostingService(this.api, this.runtime);
        this.joining = new NliJoinService(this.api, this.runtime);
    }

    @Override
    public Id id() {
        return Id.NLI_V1;
    }

    @Override
    public Set<Capability> capabilities() {
        return CAPABILITIES;
    }

    @Override
    public LinkFriendService createFriendService(String runtimeKey) {
        return new NliFriendService(runtimeKey, this.api, this.runtime);
    }

    @Override
    public LinkRuntimeService runtime() {
        return this.runtime;
    }

    @Override
    public LinkHostingService hosting() {
        return this.hosting;
    }

    @Override
    public LinkJoinService joining() {
        return this.joining;
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        return LinkService.super.shutdown().whenComplete((_, _) -> this.api.close());
    }

    private static URI defaultUri() {
        String configured = System.getProperty("netherlink.nli.url");
        if (configured == null || configured.isBlank()) configured = System.getenv("NETHERLINK_NLI_URL");
        if (configured == null || configured.isBlank()) configured = "https://nli-api.muyucloud.cool";
        return URI.create(configured.trim());
    }
}
