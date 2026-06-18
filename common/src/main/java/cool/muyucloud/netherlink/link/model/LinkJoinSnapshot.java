package cool.muyucloud.netherlink.link.model;

import org.jspecify.annotations.Nullable;

/** Token-free point-in-time state of an outgoing join operation. */
public record LinkJoinSnapshot(
    String runtimeKey,
    LinkJoinTarget target,
    LinkJoinState state,
    @Nullable LinkFailure failure
) {
}
