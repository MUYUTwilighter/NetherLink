package cool.muyucloud.netherlink.link.official;

import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.link.LinkService;
import cool.muyucloud.netherlink.link.hook.LinkContextHooks;
import cool.muyucloud.netherlink.link.official.signaling.OfficialSignalingClient;
import cool.muyucloud.netherlink.link.service.LinkFriendService;
import cool.muyucloud.netherlink.link.service.LinkHostingService;
import cool.muyucloud.netherlink.link.service.LinkJoinService;
import cool.muyucloud.netherlink.link.service.LinkRuntimeService;
import cool.muyucloud.netherlink.link.transport.OutgoingJoinService;
import net.minecraft.resources.Identifier;

import java.util.Set;

public final class OfficialLinkServiceProvider implements LinkService {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(NliConstants.MOD_ID, "official");
    public static final OfficialLinkServiceProvider INSTANCE = new OfficialLinkServiceProvider();
    private static final Set<Capability> CAPABILITIES = Set.of(
        Capability.FRIENDS,
        Capability.FRIEND_SETTINGS,
        Capability.HOSTING,
        Capability.JOINING,
        Capability.MULTI_ACCOUNT_RUNTIME
    );

    private final OfficialPresenceService presence = new OfficialPresenceService();
    private final LinkRuntimeService runtime = new OfficialRuntimeService();
    private final LinkHostingService hosting = new OfficialHostingService(this.presence);
    private final LinkJoinService joining = new OutgoingJoinService(
        runtimeKey -> LinkContextHooks.require(runtimeKey).account().getMcToken(),
        runtimeKey -> new OfficialSignalingClient(
            LinkContextHooks.require(runtimeKey).account().getMcToken(),
            "NetherLink Client Signaling-" + runtimeKey
        )
    );

    private OfficialLinkServiceProvider() {
    }

    @Override
    public Identifier id() {
        return ID;
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
