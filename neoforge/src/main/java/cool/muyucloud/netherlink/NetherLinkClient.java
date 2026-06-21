package cool.muyucloud.netherlink;

import cool.muyucloud.netherlink.teacon.CommonReg;
import cool.muyucloud.netherlink.teacon.client.renderer.IntroCardHangingSignRenderer;
import cool.muyucloud.netherlink.teacon.client.renderer.IntroCardStandingSignRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = NliConstants.MOD_ID, value = Dist.CLIENT)
public final class NetherLinkClient {
    private NetherLinkClient() {
    }

    @SubscribeEvent
    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
            CommonReg.SIGN_BLOCK_ENTITY.get(),
            IntroCardStandingSignRenderer::new
        );
        event.registerBlockEntityRenderer(
            CommonReg.HANGING_SIGN_BLOCK_ENTITY.get(),
            IntroCardHangingSignRenderer::new
        );
    }
}
