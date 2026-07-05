package cool.muyucloud.netherlink.link.model;

/** Unified status for a backend's service terms. */
public enum LinkTermsStatus {
    /** The backend does not require service-specific terms. */
    UNAVAILABLE,
    /** The currently published terms have already been accepted. */
    ACCEPTED,
    /** The backend publishes terms, but no matching acceptance was found. */
    UNACCEPTED,
    /** The backend publishes newer terms than the user's accepted revision. */
    UPDATED,
    /** The terms status could not be checked. */
    ERROR;

    public boolean isAccepted() {
        return this == UNAVAILABLE || this == ACCEPTED;
    }

    public boolean needsPrompt() {
        return !this.isAccepted();
    }
}
