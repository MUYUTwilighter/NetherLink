package cool.muyucloud.netherlink.link.model;

import java.util.UUID;

/** Public identity of the exact friend runtime selected for joining. */
public record LinkJoinTarget(UUID profileId, String presenceId) {
    public LinkJoinTarget {
        if (presenceId.isBlank()) {
            throw new IllegalArgumentException("Presence id must not be blank");
        }
    }

    /** Converts the target to the transport routing form. */
    public LinkPeerRoute route() {
        return new LinkPeerRoute(this.profileId, this.presenceId);
    }
}
