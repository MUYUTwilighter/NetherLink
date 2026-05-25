package cool.muyucloud.netherlink.mixin;

import cool.muyucloud.netherlink.access.Messenger;
import cool.muyucloud.netherlink.account.AccountManager;
import net.minecraft.Util;
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
    public abstract void sendMessage(Component component, java.util.UUID senderUUID);

    @Inject(method = "tickServer", at = @At("TAIL"))
    private void afterTickServer(BooleanSupplier haveTime, CallbackInfo ci) {
        AccountManager.tick(this.getTickCount(), this);
    }

    @Override
    public void nli$sendMessage(Supplier<Component> msg) {
        this.sendMessage(msg.get(), Util.NIL_UUID);
    }

    @Override
    public boolean nli$hasPermission(int level) {
        return true;
    }
}
