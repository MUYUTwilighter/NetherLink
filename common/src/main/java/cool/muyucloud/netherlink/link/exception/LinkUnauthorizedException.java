package cool.muyucloud.netherlink.link.exception;

import cool.muyucloud.netherlink.account.NetherLinkAuthException;
import cool.muyucloud.netherlink.link.model.LinkFailure;
import cool.muyucloud.netherlink.link.model.LinkFailureCode;

/** Compatibility authentication exception that also exposes normalized Link failure semantics. */
public class LinkUnauthorizedException extends NetherLinkAuthException implements LinkFailureSource {
    private final LinkFailure failure;

    /** Creates an unauthorized failure with a credential-safe message. */
    public LinkUnauthorizedException(String message) {
        super(message);
        this.failure = new LinkFailure(LinkFailureCode.UNAUTHORIZED, message, false);
    }

    /** Returns the normalized unauthorized failure. */
    @Override
    public LinkFailure failure() {
        return this.failure;
    }
}
