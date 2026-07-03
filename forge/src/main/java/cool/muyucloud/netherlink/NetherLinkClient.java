package cool.muyucloud.netherlink;

import cool.muyucloud.netherlink.client.NliClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(Dist.CLIENT)
public class NetherLinkClient {
    @SubscribeEvent
    public void onClientSetup(FMLClientSetupEvent event) {
        NliClient.init();
    }
}
