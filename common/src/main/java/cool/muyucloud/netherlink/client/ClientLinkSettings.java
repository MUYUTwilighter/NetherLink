package cool.muyucloud.netherlink.client;

import com.google.gson.JsonObject;
import cool.muyucloud.netherlink.NetherLinkConfig;
import cool.muyucloud.netherlink.link.LinkService;
import cool.muyucloud.netherlink.link.LinkServices;
import cool.muyucloud.netherlink.link.nli.NliLinkService;
import cool.muyucloud.netherlink.link.nli.NliV1Config;
import cool.muyucloud.netherlink.link.official.OfficialLinkServiceProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

final class ClientLinkSettings {
    private static final ResourceLocation DEFAULT_SERVICE = NliLinkService.ID;
    private static final List<ResourceLocation> AVAILABLE_SERVICES = List.of(
        NliLinkService.ID,
        OfficialLinkServiceProvider.ID
    );

    private ClientLinkSettings() {
    }

    static void applyConfiguredService(Minecraft minecraft) {
        ResourceLocation configured = configuredServiceId(minecraft);
        LinkService current = LinkServices.current();
        if (current.id().equals(configured) && !requiresReload(minecraft, current)) {
            return;
        }
        LinkServices.use(create(minecraft, configured)).join();
    }

    static List<ResourceLocation> availableServiceIds() {
        return AVAILABLE_SERVICES;
    }

    static ResourceLocation configuredServiceId(Minecraft minecraft) {
        return NetherLinkConfig.string(NetherLinkConfig.read(configPath(minecraft)), NetherLinkConfig.ACTIVE_SERVICE_KEY)
            .map(ResourceLocation::tryParse)
            .filter(AVAILABLE_SERVICES::contains)
            .orElse(DEFAULT_SERVICE);
    }

    static LinkService create(Minecraft minecraft, ResourceLocation id) {
        if (OfficialLinkServiceProvider.ID.equals(id)) {
            return OfficialLinkServiceProvider.INSTANCE;
        }
        Path path = nliPath(minecraft);
        return new NliLinkService(NliV1Config.serverUri(path), path);
    }

    static Component serviceName(ResourceLocation id) {
        return Component.translatable(id.getNamespace() + ".link." + id.getPath());
    }

    static CompletableFuture<Void> selectService(Minecraft minecraft, ResourceLocation id) {
        return selectService(minecraft, create(minecraft, id));
    }

    static CompletableFuture<Void> selectService(Minecraft minecraft, LinkService service) {
        return LinkServices.use(service).thenRun(() -> saveConfiguredService(minecraft, service.id()));
    }

    static String configuredInstanceName(Minecraft minecraft) {
        return NetherLinkConfig.instanceName(minecraft.gameDirectory.toPath()).orElse("");
    }

    static void saveInstanceName(Minecraft minecraft, String instanceName) {
        Path path = configPath(minecraft);
        JsonObject config = NetherLinkConfig.read(path);
        String trimmed = instanceName == null ? "" : instanceName.trim();
        if (trimmed.isEmpty()) {
            config.remove(NetherLinkConfig.INSTANCE_NAME_KEY);
        } else {
            config.addProperty(NetherLinkConfig.INSTANCE_NAME_KEY, trimmed);
        }
        NetherLinkConfig.write(path, config);
    }

    static boolean requiresReload(Minecraft minecraft, LinkService current) {
        return current instanceof NliLinkService nli
            && !nli.baseUri().equals(NliV1Config.serverUri(nliPath(minecraft)));
    }

    private static void saveConfiguredService(Minecraft minecraft, ResourceLocation id) {
        Path path = configPath(minecraft);
        JsonObject config = NetherLinkConfig.read(path);
        if (DEFAULT_SERVICE.equals(id)) {
            config.remove(NetherLinkConfig.ACTIVE_SERVICE_KEY);
        } else {
            config.addProperty(NetherLinkConfig.ACTIVE_SERVICE_KEY, id.toString());
        }
        NetherLinkConfig.write(path, config);
    }

    private static Path nliPath(Minecraft minecraft) {
        return NliV1Config.path(minecraft.gameDirectory.toPath());
    }

    private static Path configPath(Minecraft minecraft) {
        return NetherLinkConfig.path(minecraft.gameDirectory.toPath());
    }
}
