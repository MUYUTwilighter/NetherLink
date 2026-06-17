package cool.muyucloud.netherlink.link.exception;

import cool.muyucloud.netherlink.account.NetherLinkAuthException;

public class LinkUnauthorizedException extends NetherLinkAuthException {
    public LinkUnauthorizedException(String message) {
        super(message);
    }
}
