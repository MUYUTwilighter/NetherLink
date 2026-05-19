package cool.muyucloud.netherlink.p2p;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

public abstract class SignalingException extends RuntimeException {
    private final @Nullable UUID peerPmid;

    protected SignalingException(@Nullable UUID peerPmid, String message) {
        super(message);
        this.peerPmid = peerPmid;
    }

    public @Nullable UUID peerPmid() {
        return peerPmid;
    }

    public static final class SignalingAuthException extends SignalingException {
        public SignalingAuthException(String message) {
            super(null, message);
        }
    }

    public static final class TurnAuthFailedException extends SignalingException {
        public TurnAuthFailedException(String message) {
            super(null, message);
        }
    }

    public static final class UnknownPlayerException extends SignalingException {
        public UnknownPlayerException(@Nullable UUID peerPmid, String message) {
            super(peerPmid, message);
        }
    }

    public static class SignalingRejectedException extends SignalingException {
        public SignalingRejectedException(@Nullable UUID peerPmid, String message) {
            super(peerPmid, message);
        }
    }
}
