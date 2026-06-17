package cool.muyucloud.netherlink.link;

import net.minecraft.server.MinecraftServer;

public interface LinkHostContext extends LinkAccountContext {
    String accountName();

    MinecraftServer server();
}
