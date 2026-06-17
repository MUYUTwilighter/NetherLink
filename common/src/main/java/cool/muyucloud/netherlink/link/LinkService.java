package cool.muyucloud.netherlink.link;

import cool.muyucloud.netherlink.link.service.LinkFriendService;
import cool.muyucloud.netherlink.link.service.LinkHostingService;
import cool.muyucloud.netherlink.link.service.LinkJoinService;
import cool.muyucloud.netherlink.link.service.LinkPresenceService;

public interface LinkService {
    LinkBackendId id();

    LinkFriendService createFriendService();

    LinkPresenceService presence();

    LinkHostingService hosting();

    LinkJoinService joining();
}
