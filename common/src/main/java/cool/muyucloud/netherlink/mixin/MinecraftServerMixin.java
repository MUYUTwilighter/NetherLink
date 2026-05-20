package cool.muyucloud.netherlink.mixin;

import cool.muyucloud.netherlink.access.Messenger;
import cool.muyucloud.netherlink.account.AccountManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.PermissionSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin implements Messenger {
    @Shadow
    public abstract int getTickCount();

    @Shadow
    public abstract void sendSystemMessage(Component message);

    @Inject(method = "tickServer", at = @At("TAIL"))
    private void afterTickServer(BooleanSupplier haveTime, CallbackInfo ci) {
        AccountManager.tick(this.getTickCount(), this);
    }

    @Override
    public void nli$sendMessage(Supplier<Component> msg) {
        this.sendSystemMessage(msg.get());
    }

    @Override
    public PermissionSet nli$permissions() {
        return PermissionSet.ALL_PERMISSIONS;
    }
}
