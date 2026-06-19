package cool.muyucloud.netherlink.link.official;

import cool.muyucloud.netherlink.link.LinkService;
import cool.muyucloud.netherlink.link.hook.LinkContextHooks;
import cool.muyucloud.netherlink.link.service.LinkFriendService;
import cool.muyucloud.netherlink.link.service.LinkHostingService;
import cool.muyucloud.netherlink.link.service.LinkJoinService;
import cool.muyucloud.netherlink.link.service.LinkRuntimeService;

import java.util.Set;

public final class OfficialLinkServiceProvider implements LinkService {
    public static final OfficialLinkServiceProvider INSTANCE = new OfficialLinkServiceProvider();
    private static final Set<Capability> CAPABILITIES = Set.of(
        Capability.FRIENDS,
        Capability.HOSTING,
        Capability.JOINING,
        Capability.MULTI_ACCOUNT_RUNTIME
    );

    private final OfficialPresenceService presence = new OfficialPresenceService();
    private final LinkRuntimeService runtime = new OfficialRuntimeService();
    private final LinkHostingService hosting = new OfficialHostingService(this.presence);
    private final LinkJoinService joining = new OfficialJoinService();

    private OfficialLinkServiceProvider() {
    }

    @Override
    public Id id() {
        return Id.MOJ_26_2_S8;
    }

    @Override
    public Set<Capability> capabilities() {
        return CAPABILITIES;
    }

    @Override
    public LinkFriendService createFriendService(String runtimeKey) {
        return new OfficialFriendService(LinkContextHooks.require(runtimeKey).account());
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

}
