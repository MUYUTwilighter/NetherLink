package cool.muyucloud.netherlink;

import net.fabricmc.api.ModInitializer;

public class NetherLink implements ModInitializer {
    @Override
    public void onInitialize() {
        Constants.LOG.info("Hello Fabric world!");
        CommonClass.init();
    }
}
