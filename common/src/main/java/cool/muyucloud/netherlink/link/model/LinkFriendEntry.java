package cool.muyucloud.netherlink.link.model;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record LinkFriendEntry(UUID profileId, String name, @Nullable UUID presenceId, LinkFriendRelationship relationship, String status, boolean joinable) {
}
