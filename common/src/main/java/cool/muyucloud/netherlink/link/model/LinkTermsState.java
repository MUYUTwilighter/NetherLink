package cool.muyucloud.netherlink.link.model;

import java.util.Objects;
import java.util.Optional;

/** Result of checking a backend's current terms against its own acceptance store. */
public record LinkTermsState(LinkTermsStatus status, Optional<LinkTerms> terms, Optional<Throwable> failure) {
    public LinkTermsState {
        Objects.requireNonNull(status, "status");
        terms = Objects.requireNonNull(terms, "terms");
        failure = Objects.requireNonNull(failure, "failure");
        if (status != LinkTermsStatus.ERROR && failure.isPresent()) {
            throw new IllegalArgumentException("Only ERROR terms states may carry a failure");
        }
        if (status == LinkTermsStatus.ERROR && failure.isEmpty()) {
            throw new IllegalArgumentException("ERROR terms states must carry a failure");
        }
        if ((status == LinkTermsStatus.ACCEPTED || status == LinkTermsStatus.UNACCEPTED || status == LinkTermsStatus.UPDATED) && terms.isEmpty()) {
            throw new IllegalArgumentException(status + " terms states must carry terms");
        }
    }

    public static LinkTermsState unavailable() {
        return new LinkTermsState(LinkTermsStatus.UNAVAILABLE, Optional.empty(), Optional.empty());
    }

    public static LinkTermsState accepted(LinkTerms terms) {
        return new LinkTermsState(LinkTermsStatus.ACCEPTED, Optional.of(terms), Optional.empty());
    }

    public static LinkTermsState unaccepted(LinkTerms terms) {
        return new LinkTermsState(LinkTermsStatus.UNACCEPTED, Optional.of(terms), Optional.empty());
    }

    public static LinkTermsState updated(LinkTerms terms) {
        return new LinkTermsState(LinkTermsStatus.UPDATED, Optional.of(terms), Optional.empty());
    }

    public static LinkTermsState error(Throwable failure) {
        return new LinkTermsState(LinkTermsStatus.ERROR, Optional.empty(), Optional.of(failure));
    }

    public boolean isAccepted() {
        return this.status.isAccepted();
    }
}
