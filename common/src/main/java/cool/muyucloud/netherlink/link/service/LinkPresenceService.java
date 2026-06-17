package cool.muyucloud.netherlink.link.service;

import cool.muyucloud.netherlink.account.MinecraftAccount;

import java.util.Map;
import java.util.UUID;

public interface LinkPresenceService {
    Map<UUID, UUID> publish(MinecraftAccount account);

    void revoke(MinecraftAccount account);
}
