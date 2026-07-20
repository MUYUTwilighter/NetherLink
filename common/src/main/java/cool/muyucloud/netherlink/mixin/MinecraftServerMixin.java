package cool.muyucloud.netherlink.mixin;

import cool.muyucloud.netherlink.access.Messenger;
import cool.muyucloud.netherlink.account.AccountManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin implements Messenger {

    @Invoker("getTickCount")
    public abstract int nli$getTickCount();

    @Invoker("sendSystemMessage")
    public abstract void nli$sendSystemMessage(Component message);

    @Inject(method = "tickServer", at = @At("TAIL"))
    private void afterTickServer(BooleanSupplier haveTime, CallbackInfo ci) {
        AccountManager.tick(this.nli$getTickCount(), this);
    }

    @Override
    public void nli$sendMessage(Supplier<Component> msg) {
        this.nli$sendSystemMessage(msg.get());
    }

    @Override
    public boolean nli$hasPermission(int permissionLevel) {
        return true;
    }
}
