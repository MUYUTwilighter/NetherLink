package cool.muyucloud.netherlink.mixin;

import cool.muyucloud.netherlink.client.ClientConstants;
import net.minecraft.client.gui.screens.MultiplayerOptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiplayerOptionsScreen.class)
public abstract class MultiplayerOptionsScreenMixin extends Screen {
    @Shadow
    private IntegratedServer.MultiplayerScope wantedMultiplayerScope;

    protected MultiplayerOptionsScreenMixin(Component title) {
        super(title);
    }

    @Shadow
    protected abstract void sendPublishMessage(Component message);

    @Shadow
    protected abstract void publish(IntegratedServer singleplayerServer, IntegratedServer.MultiplayerScope scope);

    @Inject(method = "changeMultiplayerScope", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/social/PresenceHandler;tryUpdatePresence()V"))
    private void onMultiplayerScopeChanged(IntegratedServer server, CallbackInfo ci) {
        if (this.wantedMultiplayerScope == ClientConstants.INTEGRATED_SERVER) {
            if (server.unpublishServer()) {
                this.sendPublishMessage(Component.translatable("menu.multiplayerOptions.publish.stopped"));
                this.minecraft.getPlayerSocialManager().getPresenceHandler().clearInvites();
            }

            this.publish(server, ClientConstants.INTEGRATED_SERVER);
        }
    }
}
