package cool.muyucloud.netherlink.link.model;

/** Backend-neutral failure categories suitable for UI decisions and retry policy. */
public enum LinkFailureCode {
    UNAUTHORIZED,
    SERVICE_UNAVAILABLE,
    RATE_LIMITED,
    FORBIDDEN,
    PROFILE_NOT_FOUND,
    ALREADY_FRIENDS,
    REQUEST_NOT_FOUND,
    TARGET_UNAVAILABLE,
    TARGET_NOT_JOINABLE,
    NOT_FRIENDS,
    INVALID_SESSION,
    CONNECTION_LIMIT,
    NETWORK,
    TIMEOUT,
    CANCELLED,
    INTERNAL,
    UNKNOWN
}
