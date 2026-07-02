package cool.muyucloud.netherlink.link.model;

import org.jetbrains.annotations.Nullable;

/** Token-free point-in-time state of one host publication. */
public record LinkPublicationSnapshot(
    String runtimeKey,
    LinkPublicationState state,
    @Nullable LinkPresence presence,
    @Nullable LinkFailure failure
) {
}
