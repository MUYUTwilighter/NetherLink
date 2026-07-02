package cool.muyucloud.netherlink.link.model;

/** Monotonic user-visible stages of an outgoing join operation. */
public enum LinkJoinState {
    REQUESTING,
    ACCEPTED,
    NEGOTIATING,
    CONNECTING,
    CONNECTED,
    REJECTED,
    FAILED,
    CANCELLED
}
