package cool.muyucloud.netherlink.link.official;

import cool.muyucloud.netherlink.link.*;
import cool.muyucloud.netherlink.p2p.SignalingClient;

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
    public LinkFriendService createFriendService(LinkClientContext context) {
        return new OfficialFriendService(context.minecraft());
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

    @Override
    public LinkSignalingClient createSignalingClient(LinkAccountContext context, String threadName) {
        return new SignalingClient(context.account().getMcToken(), threadName);
    }

}
