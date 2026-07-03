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
    static final List<ResourceLocation> AVAILABLE_SERVICES = List.of(NliLinkService.ID, OfficialLinkServiceProvider.ID);

    private ClientLinkSettings() {
    }

    static ResourceLocation activeService(Minecraft minecraft) {
        String configured = NetherLinkConfig.string(NetherLinkConfig.read(path(minecraft)), NetherLinkConfig.ACTIVE_SERVICE_KEY)
            .or(() -> NetherLinkConfig.string(readLegacyNliConfig(minecraft), NetherLinkConfig.ACTIVE_SERVICE_KEY))
            .orElse(NliLinkService.ID.toString());
        try {
            ResourceLocation id = ResourceLocation.parse(configured);
            if (LEGACY_OFFICIAL_ID.equals(id)) {
                return OfficialLinkServiceProvider.ID;
            }
            return AVAILABLE_SERVICES.contains(id) ? id : NliLinkService.ID;
        } catch (RuntimeException ignored) {
            return NliLinkService.ID;
        }
    }

    static CompletableFuture<Void> use(Minecraft minecraft, ResourceLocation serviceId) {
        JsonObject config = NetherLinkConfig.read(path(minecraft));
        config.addProperty(NetherLinkConfig.ACTIVE_SERVICE_KEY, serviceId.toString());
        NetherLinkConfig.write(path(minecraft), config);
        return LinkServices.use(create(minecraft, serviceId));
    }

    static Component createName(ResourceLocation serviceId) {
        return Component.translatable(serviceId.getNamespace() + ".link." + serviceId.getPath());
    }

    static void applyConfiguredService(Minecraft minecraft) {
        ResourceLocation serviceId = activeService(minecraft);
        LinkService current = LinkServices.current();
        if (current.id().equals(serviceId) && !requiresNliReload(minecraft, current)) {
            return;
        }
        LinkServices.use(create(minecraft, serviceId)).join();
    }

    static void updateMinecraftSocialManager(Minecraft minecraft, LinkFriendSettings settings) {
        Object manager = minecraft.getPlayerSocialManager();
        invoke(manager, "setFriendListEnabled", settings.friendsEnabled());
        invoke(manager, "setAllowFriendRequests", settings.acceptInvites());
    }

    private static LinkService create(Minecraft minecraft, ResourceLocation serviceId) {
        if (OfficialLinkServiceProvider.ID.equals(serviceId)) {
            return OfficialLinkServiceProvider.INSTANCE;
        }
        Path path = nliPath(minecraft);
        return new NliLinkService(NliV1Config.serverUri(path), path);
    }

    private static boolean requiresNliReload(Minecraft minecraft, LinkService current) {
        return current instanceof NliLinkService nli
            && !nli.baseUri().equals(NliV1Config.serverUri(nliPath(minecraft)));
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
