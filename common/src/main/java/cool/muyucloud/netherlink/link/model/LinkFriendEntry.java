package cool.muyucloud.netherlink.link.model;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * One profile in a friend snapshot, with all active public Presence entries.
 * Pending request entries normally have an empty Presence list.
 */
public record LinkFriendEntry(UUID profileId, @Nullable String name, LinkFriendRelationship relationship, List<LinkPresence> presences) {
    public LinkFriendEntry {
        presences = List.copyOf(presences);
    }
}
