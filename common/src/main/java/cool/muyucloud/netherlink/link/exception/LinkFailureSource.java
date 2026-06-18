package cool.muyucloud.netherlink.link.exception;

import cool.muyucloud.netherlink.link.model.LinkFailure;

/** Implemented by exceptions that already carry a normalized, presentation-safe failure. */
public interface LinkFailureSource {
    /** Returns the normalized failure represented by this throwable. */
    LinkFailure failure();
}
