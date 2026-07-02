package cool.muyucloud.netherlink.link.nli;

import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.link.LinkService;
import cool.muyucloud.netherlink.link.model.LinkTerms;
import cool.muyucloud.netherlink.link.service.LinkFriendService;
import cool.muyucloud.netherlink.link.service.LinkHostingService;
import cool.muyucloud.netherlink.link.service.LinkJoinService;
import cool.muyucloud.netherlink.link.service.LinkRuntimeService;
import net.minecraft.resources.ResourceLocation;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class NliLinkService implements LinkService {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(NliConstants.MOD_ID, "nli_v1");
    public static final NliLinkService INSTANCE = new NliLinkService(defaultUri());
    private static final Set<Capability> CAPABILITIES = Set.of(
        Capability.FRIENDS,
        Capability.FRIEND_SETTINGS,
        Capability.HOSTING,
        Capability.JOINING,
        Capability.MULTI_ACCOUNT_RUNTIME,
        Capability.MULTI_PRESENCE
    );

    private final NliApiClient api;
    private final URI baseUri;
    private final NliRuntimeService runtime;
    private final NliHostingService hosting;
    private final NliJoinService joining;

    public NliLinkService(URI baseUri) {
        this.baseUri = baseUri;
        this.api = new NliApiClient(baseUri);
        this.runtime = new NliRuntimeService(this.api);
        this.hosting = new NliHostingService(this.api, this.runtime);
        this.joining = new NliJoinService(this.api, this.runtime);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    public URI baseUri() {
        return this.baseUri;
    }

    @Override
    public Set<Capability> capabilities() {
        return CAPABILITIES;
    }

    @Override
    public CompletableFuture<Optional<LinkTerms>> terms(String language) {
        String requestedLanguage = language == null || language.isBlank() ? "en" : language;
        return this.api.getPublicText("v1/terms", requestedLanguage)
            .thenApply(text -> Optional.of(new LinkTerms(requestedLanguage, text, fingerprint(text))));
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
        return LinkService.super.shutdown().whenComplete((ignored1, ignored101) -> this.api.close());
    }

    private static URI defaultUri() {
        String configured = System.getProperty("netherlink.nli.url");
        if (configured == null || configured.isBlank()) configured = System.getenv("NETHERLINK_NLI_URL");
        if (configured != null && !configured.isBlank()) {
            return URI.create(configured.trim());
        }
        return NliV1Config.serverUri(NliV1Config.path(Path.of("")));
    }

    private static String fingerprint(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}
