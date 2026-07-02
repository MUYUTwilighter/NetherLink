package cool.muyucloud.netherlink.link.exception;

import cool.muyucloud.netherlink.link.model.LinkFailure;
import cool.muyucloud.netherlink.link.model.LinkFailureCode;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/** Utilities for normalizing asynchronous and foreign exceptions at the Link boundary. */
public final class LinkFailures {
    private LinkFailures() {
    }

    /**
     * Unwraps common future wrappers and maps a throwable to a stable failure category.
     * Unknown errors deliberately remain {@link LinkFailureCode#UNKNOWN}.
     */
    public static LinkFailure from(Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof LinkFailureSource source) {
            return source.failure();
        }
        if (cause instanceof CancellationException) {
            return new LinkFailure(LinkFailureCode.CANCELLED, message(cause), false);
        }
        if (cause instanceof TimeoutException || message(cause).toLowerCase(Locale.ROOT).contains("timed out")) {
            return new LinkFailure(LinkFailureCode.TIMEOUT, message(cause), true);
        }
        if (cause instanceof IOException) {
            return new LinkFailure(LinkFailureCode.NETWORK, message(cause), true);
        }
        if (cause instanceof InterruptedException) {
            return new LinkFailure(LinkFailureCode.CANCELLED, message(cause), true);
        }
        return new LinkFailure(LinkFailureCode.UNKNOWN, message(cause), false);
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof ExecutionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String message(Throwable error) {
        return error.getMessage() != null && !error.getMessage().isBlank()
            ? error.getMessage()
            : error.getClass().getSimpleName();
    }
}
