package cool.muyucloud.netherlink.link.transport;

import cool.muyucloud.netherlink.link.exception.LinkFailureSource;
import cool.muyucloud.netherlink.link.model.LinkFailure;
import cool.muyucloud.netherlink.link.model.LinkFailureCode;
import cool.muyucloud.netherlink.link.model.LinkPeerRoute;
import org.jspecify.annotations.Nullable;

/** Internal signaling error carrying an optional peer route and normalized public failure. */
public abstract class SignalingException extends RuntimeException implements LinkFailureSource {
    private final @Nullable LinkPeerRoute peer;
    private final LinkFailure failure;

    protected SignalingException(@Nullable LinkPeerRoute peer, LinkFailure failure) {
        super(failure.message());
        this.peer = peer;
        this.failure = failure;
    }

    public @Nullable LinkPeerRoute peer() {
        return this.peer;
    }

    @Override
    public LinkFailure failure() {
        return this.failure;
    }

    public static final class SignalingAuthException extends SignalingException {
        public SignalingAuthException(String message) {
            super(null, new LinkFailure(LinkFailureCode.UNAUTHORIZED, message, false));
        }
    }

    public static final class TurnAuthFailedException extends SignalingException {
        public TurnAuthFailedException(String message) {
            super(null, new LinkFailure(LinkFailureCode.SERVICE_UNAVAILABLE, message, true));
        }
    }

    public static final class UnknownPlayerException extends SignalingException {
        public UnknownPlayerException(@Nullable LinkPeerRoute peer, String message) {
            super(peer, new LinkFailure(LinkFailureCode.PROFILE_NOT_FOUND, message, false));
        }
    }

    public static class SignalingRejectedException extends SignalingException {
        public SignalingRejectedException(@Nullable LinkPeerRoute peer, String message) {
            super(peer, new LinkFailure(LinkFailureCode.FORBIDDEN, message, false));
        }
    }
}
