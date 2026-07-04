package cool.muyucloud.netherlink.link.nli;

import com.google.gson.JsonObject;
import cool.muyucloud.netherlink.NetherLinkConfig;
import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.http.JsonHttp;
import cool.muyucloud.netherlink.link.LinkService;
import cool.muyucloud.netherlink.link.model.LinkTerms;
import cool.muyucloud.netherlink.link.model.LinkTermsState;
import cool.muyucloud.netherlink.link.service.LinkFriendService;
import cool.muyucloud.netherlink.link.service.LinkHostingService;
import cool.muyucloud.netherlink.link.service.LinkJoinService;
import cool.muyucloud.netherlink.link.service.LinkRuntimeService;
import net.minecraft.resources.Identifier;

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
    public static final Identifier ID = Identifier.fromNamespaceAndPath(NliConstants.MOD_ID, "nli_v1");
    public static final NliLinkService INSTANCE = new NliLinkService(defaultUri(), NliV1Config.path(Path.of("")));
    private static final Set<Capability> CAPABILITIES = Set.of(
        Capability.FRIENDS,
        Capability.FRIEND_SETTINGS,
        Capability.HOSTING,
        Capability.JOINING,
        Capability.SELF_PRESENCE,
        Capability.MULTI_ACCOUNT_RUNTIME,
        Capability.MULTI_PRESENCE
    );

    private final NliApiClient api;
    private final URI baseUri;
    private final Path configPath;
    private final Object termsLock = new Object();
    private final NliRuntimeService runtime;
    private final NliHostingService hosting;
    private final NliJoinService joining;

    public NliLinkService(URI baseUri) {
        this(baseUri, NliV1Config.path(Path.of("")));
    }

    public NliLinkService(URI baseUri, Path configPath) {
        this.baseUri = baseUri;
        this.configPath = configPath;
        this.api = new NliApiClient(baseUri);
        this.runtime = new NliRuntimeService(this.api);
        this.hosting = new NliHostingService(this.api, this.runtime);
        this.joining = new NliJoinService(this.api, this.runtime);
    }

    @Override
    public Identifier id() {
        return ID;
    }

    public URI baseUri() {
        return this.baseUri;
    }

    public Path configPath() {
        return this.configPath;
    }

    @Override
    public String termsCacheScope() {
        return this.id() + "@" + this.baseUri;
    }

    @Override
    public Set<Capability> capabilities() {
        return CAPABILITIES;
    }

    @Override
    public CompletableFuture<Optional<LinkTerms>> terms(String language) {
        return this.currentTerms(language).thenApply(Optional::of);
    }

    @Override
    public CompletableFuture<LinkTermsState> termsStatus(String language) {
        return this.currentTerms(language).thenApply(terms -> {
            Optional<String> accepted = this.acceptedRevision(terms.language());
            return accepted.map(s -> terms.revision().equals(s)
                ? LinkTermsState.accepted(terms)
                : LinkTermsState.updated(terms)).orElseGet(() -> LinkTermsState.unaccepted(terms));
        });
    }

    @Override
    public CompletableFuture<Void> acceptTerms(LinkTerms terms) {
        try {
            synchronized (this.termsLock) {
                JsonObject config = NliV1Config.read(this.configPath);
                JsonObject accepted = NliV1Config.acceptedTerms(config);
                accepted.addProperty(key(this.id(), terms.language()), terms.revision());
                NliConstants.LOG.debug("Saving accepted NLI v1 terms to {}", this.configPath);
                NliV1Config.write(this.configPath, config);
            }
            return CompletableFuture.completedFuture(null);
        } catch (RuntimeException error) {
            return CompletableFuture.failedFuture(new IllegalStateException("Unable to save accepted NLI v1 terms", error));
        }
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

    private CompletableFuture<LinkTerms> currentTerms(String language) {
        String requestedLanguage = language == null || language.isBlank() ? "en" : language;
        return this.api.getPublicText("v1/terms", requestedLanguage)
            .thenApply(text -> new LinkTerms(requestedLanguage, text, fingerprint(text)));
    }

    private Optional<String> acceptedRevision(String language) {
        synchronized (this.termsLock) {
            JsonObject accepted = NliV1Config.acceptedTerms(NliV1Config.read(this.configPath));
            Optional<String> revision = JsonHttp.stringOptional(accepted, key(this.id(), language));
            if (revision.isPresent()) {
                return revision;
            }
            Path legacyPath = this.configPath.resolveSibling("config.json");
            JsonObject legacy = NetherLinkConfig.read(legacyPath);
            return JsonHttp.stringOptional(JsonHttp.object(legacy, NliV1Config.ACCEPTED_TERMS_KEY), key(this.id(), language));
        }
    }
    private static String key(Identifier backendId, String language) {
        return backendId + "|" + language;
    }

    private static String fingerprint(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}
