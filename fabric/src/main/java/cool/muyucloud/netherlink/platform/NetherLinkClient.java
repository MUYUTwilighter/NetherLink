package cool.muyucloud.netherlink.platform;

import cool.muyucloud.netherlink.client.NliClient;
import net.fabricmc.api.ClientModInitializer;

public class NetherLinkClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        NliClient.init();
    }
}
