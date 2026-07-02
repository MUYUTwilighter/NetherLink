package cool.muyucloud.netherlink.link.model;

import org.jspecify.annotations.Nullable;

/** Token-free point-in-time state of an account runtime. */
public record LinkRuntimeSnapshot(
    String runtimeKey,
    LinkRuntimeState state,
    @Nullable LinkRuntimeIdentity identity,
    @Nullable LinkFailure failure
) {
}
