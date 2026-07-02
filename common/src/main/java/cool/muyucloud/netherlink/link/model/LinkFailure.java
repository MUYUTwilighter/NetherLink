package cool.muyucloud.netherlink.link.model;

/**
 * Sanitized failure information safe to expose outside a backend implementation.
 *
 * @param code stable semantic category; never a backend wire error code
 * @param message human-readable detail that must not contain credentials or transport payloads
 * @param retryable whether retrying later without changing user input may succeed
 */
public record LinkFailure(LinkFailureCode code, String message, boolean retryable) {
    public LinkFailure {
        if (message.isBlank()) {
            message = code.name();
        }
    }
}
