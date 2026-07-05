package cool.muyucloud.netherlink.link.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Complete account friend graph divided into established, incoming, and outgoing relationships,
 * with active Presence entries published by the caller's own profile.
 */
public record LinkFriendSnapshot(
    List<LinkPresence> selfPresences,
    List<LinkFriendEntry> friends,
    List<LinkFriendEntry> incoming,
    List<LinkFriendEntry> outgoing
) {
    public LinkFriendSnapshot(List<LinkFriendEntry> friends, List<LinkFriendEntry> incoming, List<LinkFriendEntry> outgoing) {
        this(List.of(), friends, incoming, outgoing);
    }

    public LinkFriendSnapshot {
        selfPresences = List.copyOf(selfPresences);
        friends = List.copyOf(friends);
        incoming = List.copyOf(incoming);
        outgoing = List.copyOf(outgoing);
    }

    /** Returns an immutable concatenation of all three relationship lists. */
    public List<LinkFriendEntry> all() {
        List<LinkFriendEntry> entries = new ArrayList<>();
        entries.addAll(this.friends);
        entries.addAll(this.incoming);
        entries.addAll(this.outgoing);
        return List.copyOf(entries);
    }
}