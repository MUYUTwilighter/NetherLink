package cool.muyucloud.netherlink.link;

import cool.muyucloud.netherlink.link.official.OfficialLinkServiceProvider;

public final class LinkServices {
    private static volatile LinkService service = OfficialLinkServiceProvider.INSTANCE;

    private LinkServices() {
    }

    public static LinkService current() {
        return service;
    }

    public static void use(LinkService nextService) {
        service = nextService;
    }
}
