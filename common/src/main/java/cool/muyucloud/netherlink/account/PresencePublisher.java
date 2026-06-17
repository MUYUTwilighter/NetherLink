package cool.muyucloud.netherlink.account;

import cool.muyucloud.netherlink.link.LinkServices;
import cool.muyucloud.netherlink.link.LinkUnauthorizedException;

import java.util.Map;
import java.util.UUID;

@Deprecated(forRemoval = true)
public class PresencePublisher {
    public Map<UUID, UUID> publish(MinecraftAccount account) {
        return LinkServices.current().presence().publish(account);
    }

    public void revoke(MinecraftAccount account) {
        LinkServices.current().presence().revoke(account);
    }

    public static class UnauthorizedException extends LinkUnauthorizedException {
        public UnauthorizedException(String message) {
            super(message);
        }
    }
}
