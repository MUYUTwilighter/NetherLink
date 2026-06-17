package cool.muyucloud.netherlink.link;

import java.util.ArrayList;
import java.util.List;

public record LinkFriendSnapshot(List<LinkFriendEntry> friends, List<LinkFriendEntry> incoming, List<LinkFriendEntry> outgoing) {
    public List<LinkFriendEntry> all() {
        List<LinkFriendEntry> entries = new ArrayList<>();
        entries.addAll(this.friends);
        entries.addAll(this.incoming);
        entries.addAll(this.outgoing);
        return entries;
    }
}
