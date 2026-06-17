package cool.muyucloud.netherlink.link;

import net.minecraft.server.MinecraftServer;

public interface LinkHostPublication {
    MinecraftServer server();

    void refresh();

    void revoke();
}
