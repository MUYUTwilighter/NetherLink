package cool.muyucloud.netherlink;

import cool.muyucloud.netherlink.teacon.CommonReg;
import cool.muyucloud.netherlink.teacon.client.renderer.IntroCardHangingSignRenderer;
import cool.muyucloud.netherlink.teacon.client.renderer.IntroCardStandingSignRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@Mod(value = NliConstants.MOD_ID, dist = Dist.CLIENT)
public class NetherLinkClient {
    public NetherLinkClient(IEventBus eventBus) {
        eventBus.addListener(this::onRegisterRenderers);
    }

    private void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
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
