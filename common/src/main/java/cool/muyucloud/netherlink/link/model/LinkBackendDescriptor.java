package cool.muyucloud.netherlink.link.model;

import cool.muyucloud.netherlink.link.LinkBackendId;

import java.util.Set;

/**
 * Stable public metadata for one backend version.
 * @param id backend/protocol identifier
 * @param capabilities immutable set of supported optional features
 */
public record LinkBackendDescriptor(LinkBackendId id, Set<LinkBackendCapability> capabilities) {
    public LinkBackendDescriptor {
        capabilities = Set.copyOf(capabilities);
    }

    /** Returns whether the backend advertises an optional feature. */
    @SuppressWarnings("unused")
    public boolean supports(LinkBackendCapability capability) {
        return this.capabilities.contains(capability);
    }
}
