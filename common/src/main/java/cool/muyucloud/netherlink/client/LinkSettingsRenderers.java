package cool.muyucloud.netherlink.client;

import cool.muyucloud.netherlink.link.LinkService;
import cool.muyucloud.netherlink.link.nli.NliLinkService;
import cool.muyucloud.netherlink.link.official.OfficialLinkServiceProvider;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class LinkSettingsRenderers {
    private static final Map<ResourceLocation, Factory> FACTORIES = new ConcurrentHashMap<>();

    static {
        register(NliLinkService.ID, FriendNetworkSettingsRenderer::new);
        register(OfficialLinkServiceProvider.ID, FriendNetworkSettingsRenderer::new);
    }

    private LinkSettingsRenderers() {
    }

    static void register(ResourceLocation id, Factory factory) {
        FACTORIES.put(id, factory);
    }

    static LinkSettingsRenderer create(LinkSettingsContext context) {
        LinkService service = context.service();
        Factory factory = FACTORIES.get(service.settingsRendererId());
        if (factory != null) {
            return factory.create(context);
        }
        if (service.supports(LinkService.Capability.FRIEND_SETTINGS)) {
            return new FriendNetworkSettingsRenderer(context);
        }
        return new EmptyLinkSettingsRenderer(context);
    }

    interface Factory {
        LinkSettingsRenderer create(LinkSettingsContext context);
    }
}
