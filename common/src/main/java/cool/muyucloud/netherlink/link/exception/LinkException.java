package cool.muyucloud.netherlink.link.exception;

import cool.muyucloud.netherlink.link.model.LinkFailure;

/** General unchecked Link failure with backend-neutral semantics. */
public class LinkException extends RuntimeException implements LinkFailureSource {
    private final LinkFailure failure;

    /** Creates an exception for a normalized failure. */
    public LinkException(LinkFailure failure) {
        super(failure.message());
        this.failure = failure;
    }

    /** Creates an exception while retaining an internal cause for diagnostics. */
    @SuppressWarnings("unused")
    public LinkException(LinkFailure failure, Throwable cause) {
        super(failure.message(), cause);
        this.failure = failure;
    }

    /** Returns the presentation-safe failure; callers should not derive behavior from exception text. */
    @Override
    public LinkFailure failure() {
        return this.failure;
    }
}
