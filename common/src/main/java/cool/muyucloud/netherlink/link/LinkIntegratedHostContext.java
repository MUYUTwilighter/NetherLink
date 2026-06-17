package cool.muyucloud.netherlink.link;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;

public interface LinkIntegratedHostContext extends LinkHostContext {
    Minecraft minecraft();

    IntegratedServer integratedServer();

    void setFriendsOpen(boolean open);

    void message(Component message);
}
