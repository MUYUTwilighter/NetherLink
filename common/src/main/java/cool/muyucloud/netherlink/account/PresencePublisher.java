package cool.muyucloud.netherlink.account;

import cool.muyucloud.netherlink.link.LinkServices;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("unused")
@Deprecated(forRemoval = true)
public class PresencePublisher {
    public Map<UUID, UUID> publish(MinecraftAccount account) {
        return LinkServices.current().presence().publish(account);
    }

    public void revoke(MinecraftAccount account) {
        LinkServices.current().presence().revoke(account);
    }

}
