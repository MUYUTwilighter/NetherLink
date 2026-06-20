package cool.muyucloud.netherlink.link.model;

/** Terms supplied by a backend for a particular display language. */
public record LinkTerms(String language, String text, String revision) {
    public LinkTerms {
        if (language == null || language.isBlank()) {
            throw new IllegalArgumentException("Terms language must not be blank");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Terms text must not be blank");
        }
        if (revision == null || revision.isBlank()) {
            throw new IllegalArgumentException("Terms revision must not be blank");
        }
    }
}
