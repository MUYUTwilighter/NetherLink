package cool.muyucloud.netherlink.mixin;

import cool.muyucloud.netherlink.access.MinecraftConnectionAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin implements MinecraftConnectionAccess {
    @Shadow
    private @Nullable Connection pendingConnection;

    @Override
    public void nli$setPendingConnection(Connection connection) {
        this.pendingConnection = connection;
    }
}
