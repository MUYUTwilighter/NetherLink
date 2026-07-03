package cool.muyucloud.netherlink.client;

import com.google.gson.JsonObject;
import cool.muyucloud.netherlink.NetherLinkConfig;
import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.link.LinkService;
import cool.muyucloud.netherlink.link.LinkServices;
import cool.muyucloud.netherlink.link.model.LinkFriendSettings;
import cool.muyucloud.netherlink.link.nli.NliLinkService;
import cool.muyucloud.netherlink.link.nli.NliV1Config;
import cool.muyucloud.netherlink.link.official.OfficialLinkServiceProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

final class ClientLinkSettings {
    private static final ResourceLocation LEGACY_OFFICIAL_ID = ResourceLocation.fromNamespaceAndPath(NliConstants.MOD_ID, "moj_26_2_s8");
    private static final ResourceLocation LEGACY_OFFICIAL_ID_V1 = ResourceLocation.fromNamespaceAndPath(NliConstants.MOD_ID, "moj_26_2");
    static final List<ResourceLocation> AVAILABLE_SERVICES = List.of(NliLinkService.ID, OfficialLinkServiceProvider.ID);

    private ClientLinkSettings() {
    }

    static ResourceLocation activeService(Minecraft minecraft) {
        String configured = NetherLinkConfig.string(NetherLinkConfig.read(path(minecraft)), NetherLinkConfig.ACTIVE_SERVICE_KEY)
            .or(() -> NetherLinkConfig.string(readLegacyNliConfig(minecraft), NetherLinkConfig.ACTIVE_SERVICE_KEY))
            .orElse(NliLinkService.ID.toString());
        try {
            ResourceLocation id = ResourceLocation.parse(configured);
            if (LEGACY_OFFICIAL_ID.equals(id) || LEGACY_OFFICIAL_ID_V1.equals(id)) {
                return OfficialLinkServiceProvider.ID;
            }
            return AVAILABLE_SERVICES.contains(id) ? id : NliLinkService.ID;
        } catch (RuntimeException ignored) {
            return NliLinkService.ID;
        }
    }

    static Component serviceName(ResourceLocation serviceId) {
        return Component.translatable(serviceId.getNamespace() + ".link." + serviceId.getPath());
    }

    static CompletableFuture<Void> selectService(Minecraft minecraft, ResourceLocation serviceId) {
        return selectService(minecraft, create(minecraft, serviceId));
    }

    static CompletableFuture<Void> selectService(Minecraft minecraft, LinkService service) {
        return LinkServices.use(service).thenRun(() -> saveConfiguredService(minecraft, service.id()));
    }

    static void applyConfiguredService(Minecraft minecraft) {
        ResourceLocation serviceId = activeService(minecraft);
        LinkService current = LinkServices.current();
        if (current.id().equals(serviceId) && !requiresReload(minecraft, current)) {
            return;
        }
        LinkServices.use(create(minecraft, serviceId)).join();
    }

    static List<ResourceLocation> availableServiceIds() {
        return AVAILABLE_SERVICES;
    }

    static void updateMinecraftSocialManager(Minecraft minecraft, LinkFriendSettings settings) {
        Object manager = minecraft.getPlayerSocialManager();
        invoke(manager, "setFriendListEnabled", settings.friendsEnabled());
        invoke(manager, "setAllowFriendRequests", settings.acceptInvites());
    }

    static String configuredInstanceName(Minecraft minecraft) {
        return NetherLinkConfig.instanceName(minecraft.gameDirectory.toPath()).orElse("");
    }

    static void saveInstanceName(Minecraft minecraft, String instanceName) {
        Path path = path(minecraft);
        JsonObject config = NetherLinkConfig.read(path);
        String trimmed = instanceName == null ? "" : instanceName.trim();
        if (trimmed.isEmpty()) {
            config.remove(NetherLinkConfig.INSTANCE_NAME_KEY);
        } else {
            config.addProperty(NetherLinkConfig.INSTANCE_NAME_KEY, trimmed);
        }
        NetherLinkConfig.write(path, config);
    }

    static LinkService create(Minecraft minecraft, ResourceLocation serviceId) {
        if (OfficialLinkServiceProvider.ID.equals(serviceId)) {
            return OfficialLinkServiceProvider.INSTANCE;
        }
        Path path = nliPath(minecraft);
        return new NliLinkService(NliV1Config.serverUri(path), path);
    }

    static boolean requiresReload(Minecraft minecraft, LinkService current) {
        return current instanceof NliLinkService nli
            && !nli.baseUri().equals(NliV1Config.serverUri(nliPath(minecraft)));
    }

    private static void saveConfiguredService(Minecraft minecraft, ResourceLocation id) {
        Path path = path(minecraft);
        JsonObject config = NetherLinkConfig.read(path);
        if (NliLinkService.ID.equals(id)) {
            config.remove(NetherLinkConfig.ACTIVE_SERVICE_KEY);
        } else {
            config.addProperty(NetherLinkConfig.ACTIVE_SERVICE_KEY, id.toString());
        }
        NetherLinkConfig.write(path, config);
    }

    private static void invoke(Object target, String name, boolean value) {
        try {
            Method method = target.getClass().getMethod(name, boolean.class);
            method.invoke(target, value);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static Path path(Minecraft minecraft) {
        return NetherLinkConfig.path(minecraft.gameDirectory.toPath());
    }

    private static Path nliPath(Minecraft minecraft) {
        return NliV1Config.path(minecraft.gameDirectory.toPath());
    }

    private static JsonObject readLegacyNliConfig(Minecraft minecraft) {
        try {
            return NliV1Config.read(nliPath(minecraft));
        } catch (RuntimeException error) {
            NliConstants.LOG.warn("Unable to read legacy NetherLink settings from {}; ignoring legacy values", nliPath(minecraft), error);
            return new JsonObject();
        }
    }

}
