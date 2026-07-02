package cool.muyucloud.netherlink.mixin;

import com.mojang.blaze3d.platform.WindowEventHandler;
import cool.muyucloud.netherlink.access.MinecraftAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin extends ReentrantBlockableEventLoop<Runnable> implements WindowEventHandler, MinecraftAccess {
    public MinecraftMixin(String name) {
        super(name);
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
}
