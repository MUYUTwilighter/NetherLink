package cool.muyucloud.netherlink.platform;

import cool.muyucloud.netherlink.CommonSetup;
import cool.muyucloud.netherlink.Constants;
import net.fabricmc.api.ModInitializer;

public class NetherLink implements ModInitializer {
    @Override
    public void onInitialize() {
        Constants.LOG.info("Hello Fabric world!");
        CommonSetup.init();
    }
}
