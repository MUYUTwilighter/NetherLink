package cool.muyucloud.netherlink.account;

public class NetherLinkAuthException extends RuntimeException {
    public NetherLinkAuthException(String message) {
        super(message);
    }

    public NetherLinkAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
