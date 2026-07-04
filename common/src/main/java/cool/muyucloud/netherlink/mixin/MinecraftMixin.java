package cool.muyucloud.netherlink.mixin;

import com.mojang.blaze3d.platform.WindowEventHandler;
import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.access.MinecraftAccess;
import cool.muyucloud.netherlink.client.ClientTermsController;
import cool.muyucloud.netherlink.link.LinkServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import net.minecraft.network.Connection;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin extends ReentrantBlockableEventLoop<Runnable> implements WindowEventHandler, MinecraftAccess {
    public MinecraftMixin(String name, boolean propagatesCrashes) {
        super(name, propagatesCrashes);
    }

    @Shadow
    private @Nullable Connection pendingConnection;

    @Shadow
    protected abstract String createTitle();

    @Override
    public String nli$createWindowTitle() {
        return this.createTitle();
    }

    @Override
    public void nli$setPendingConnection(Connection connection) {
        this.pendingConnection = connection;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void netherlink$prefetchTerms(GameConfig gameConfig, CallbackInfo callback) {
        Minecraft minecraft = (Minecraft)(Object)this;
        NliConstants.gameDirectory = minecraft.gameDirectory::toPath;
        NliConstants.windowTitle = () -> Optional.ofNullable(MinecraftAccess.createWindowTitle());
        ClientTermsController.prefetch(minecraft);
    }

    @Inject(method = "destroy", at = @At("HEAD"))
    private void netherlink$shutdownLinkService(CallbackInfo callback) {
        try {
            LinkServices.current().shutdown().orTimeout(5L, TimeUnit.SECONDS).join();
        } catch (RuntimeException error) {
            NliConstants.LOG.warn("Failed to close NetherLink runtimes during client shutdown", error);
        }
    }
}
