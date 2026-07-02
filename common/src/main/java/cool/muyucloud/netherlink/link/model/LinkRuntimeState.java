package cool.muyucloud.netherlink.link.model;

/** User-visible lifecycle states of an account runtime. */
public enum LinkRuntimeState {
    OPENING,
    ACTIVE,
    RENEWING,
    DEGRADED,
    CLOSING,
    CLOSED,
    FAILED
}
