package cool.muyucloud.netherlink.link;

import net.minecraft.client.Minecraft;

public interface LinkClientContext extends LinkAccountContext {
    Minecraft minecraft();
}
