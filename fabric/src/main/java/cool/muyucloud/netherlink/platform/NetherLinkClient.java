package cool.muyucloud.netherlink.platform;

import cool.muyucloud.netherlink.teacon.client.TeaconClientActions;
import net.fabricmc.api.ClientModInitializer;

public class NetherLinkClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        TeaconClientActions.install();
    }
}
