package cool.muyucloud.netherlink.mixin;

import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.link.LinkServices;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.TimeUnit;

@Mixin(Minecraft.class)
public abstract class MinecraftLifecycleMixin {
    @Inject(method = "destroy", at = @At("HEAD"))
    private void netherlink$shutdownLinkService(CallbackInfo callback) {
        try {
            LinkServices.current().shutdown().orTimeout(5L, TimeUnit.SECONDS).join();
        } catch (RuntimeException error) {
            NliConstants.LOG.warn("Failed to close NetherLink runtimes during client shutdown", error);
        }
    }
}
