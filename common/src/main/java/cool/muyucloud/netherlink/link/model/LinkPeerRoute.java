package cool.muyucloud.netherlink.link.model;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Transport routing identity for one peer runtime.
 * The profile id may be absent when a signaling backend routes only by Presence id.
 */
public record LinkPeerRoute(@Nullable UUID profileId, String presenceId) {
    public LinkPeerRoute {
        if (presenceId.isBlank()) {
            throw new IllegalArgumentException("Presence id must not be blank");
        }
    }
}
