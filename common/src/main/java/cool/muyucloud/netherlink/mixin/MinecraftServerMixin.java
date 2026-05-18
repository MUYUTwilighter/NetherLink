package cool.muyucloud.netherlink.mixin;

import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.access.Messenger;
import cool.muyucloud.netherlink.account.AccountManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
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

    @Inject(method = "tickChildren", at = @At("TAIL"))
    private void afterTickChildren(BooleanSupplier haveTime, CallbackInfo ci) {
        AccountManager.dumpMessages();
        if (this.getTickCount() % NliConstants.INTERVAL_TOKEN == 0) {
            AccountManager.refresh(false, this);
        }
    }

    @Override
    public void cif$sendMessage(Supplier<Component> msg) {
        this.sendSystemMessage(msg.get());
    }
}
