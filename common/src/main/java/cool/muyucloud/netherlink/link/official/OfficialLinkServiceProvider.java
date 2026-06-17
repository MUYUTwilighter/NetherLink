package cool.muyucloud.netherlink.link.official;

import cool.muyucloud.netherlink.link.LinkBackendId;
import cool.muyucloud.netherlink.link.LinkService;
import cool.muyucloud.netherlink.link.hook.LinkClientHooks;
import cool.muyucloud.netherlink.link.service.LinkFriendService;
import cool.muyucloud.netherlink.link.service.LinkHostingService;
import cool.muyucloud.netherlink.link.service.LinkJoinService;
import cool.muyucloud.netherlink.link.service.LinkPresenceService;

public final class OfficialLinkServiceProvider implements LinkService {
    public static final OfficialLinkServiceProvider INSTANCE = new OfficialLinkServiceProvider();

    private final OfficialPresenceService presence = new OfficialPresenceService();
    private final LinkHostingService hosting = new OfficialHostingService(this.presence);
    private final LinkJoinService joining = new OfficialJoinService();

    private OfficialLinkServiceProvider() {
    }

    @Override
    public LinkBackendId id() {
        return LinkBackendId.MOJ_26_2_S8;
    }

    @Override
    public LinkFriendService createFriendService() {
        return new OfficialFriendService(LinkClientHooks.requireClient().minecraft());
    }

    @Override
    public LinkPresenceService presence() {
        return this.presence;
    }

    @Override
    public LinkHostingService hosting() {
        return this.hosting;
    }

    @Override
    public LinkJoinService joining() {
        return this.joining;
    }

}
