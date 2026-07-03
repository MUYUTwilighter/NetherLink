package cool.muyucloud.netherlink;

import cool.muyucloud.netherlink.client.NliClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(Dist.CLIENT)
public class NetherLinkClient {
    @SubscribeEvent
    public static void initClient(FMLClientSetupEvent event) {
        NliClient.init();
    }
}
