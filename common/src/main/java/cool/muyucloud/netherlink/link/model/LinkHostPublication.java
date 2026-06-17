package cool.muyucloud.netherlink.link.model;

import net.minecraft.server.MinecraftServer;

public interface LinkHostPublication {
    MinecraftServer server();

    void refresh();

    void revoke();
}
