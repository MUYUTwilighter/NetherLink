package cool.muyucloud.netherlink.link;

public interface LinkService {
    LinkBackendId id();

    LinkFriendService createFriendService(LinkClientContext context);

    LinkPresenceService presence();

    LinkHostingService hosting();

    LinkJoinService joining();

    LinkSignalingClient createSignalingClient(LinkAccountContext context, String threadName);
}
