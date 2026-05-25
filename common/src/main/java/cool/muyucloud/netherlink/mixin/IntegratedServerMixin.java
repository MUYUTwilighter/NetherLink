package cool.muyucloud.netherlink.mixin;

import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.client.ClientLanSettings;
import cool.muyucloud.netherlink.client.ClientP2PController;
import cool.muyucloud.netherlink.client.NetherLinkIntegratedServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin implements NetherLinkIntegratedServer {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Unique
    private boolean netherlink$friendsOpen;

    @Override
    public boolean nli$isFriendsOpen() {
        return this.netherlink$friendsOpen;
    }

    @Override
    public void nli$setFriendsOpen(boolean friendsOpen) {
        this.netherlink$friendsOpen = friendsOpen;
    }

    @Inject(method = "publishServer", at = @At("RETURN"))
    private void onPublishServer(@Nullable GameType gameMode, boolean allowCommands, int port, CallbackInfoReturnable<Boolean> cir) {
        NliConstants.LOG.info(
            "[P2P][client] IntegratedServer.publishServer returned {} friendsOpen={}",
            cir.getReturnValueZ(),
            ClientLanSettings.friendsOpen()
        );
        if (cir.getReturnValueZ()) {
            ClientP2PController.setFriendsOpen(this.minecraft, (IntegratedServer)(Object)this, ClientLanSettings.friendsOpen());
        }
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void onStopServer(CallbackInfo ci) {
        this.netherlink$shutdownClientP2P();
    }

    @Inject(method = "halt", at = @At("HEAD"))
    private void onHalt(boolean wait, CallbackInfo ci) {
        this.netherlink$shutdownClientP2P();
    }

    @Unique
    private void netherlink$shutdownClientP2P() {
        this.netherlink$friendsOpen = false;
        ClientP2PController.shutdown();
    }
}
