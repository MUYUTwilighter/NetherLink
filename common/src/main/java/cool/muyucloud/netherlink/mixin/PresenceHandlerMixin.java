package cool.muyucloud.netherlink.mixin;

import com.mojang.authlib.yggdrasil.response.PresenceStatus;
import cool.muyucloud.netherlink.client.ClientConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PresenceSharing;
import net.minecraft.client.gui.screens.social.PresenceHandler;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static net.minecraft.client.server.IntegratedServer.MultiplayerScope.*;

@Mixin(PresenceHandler.class)
public class PresenceHandlerMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "getPresenceStatus", at = @At("HEAD"), cancellable = true)
    public void onGetPresenceStatus(CallbackInfoReturnable<PresenceStatus> cir) {
        cir.cancel();
        PresenceSharing maySharing = this.minecraft.options.sharePresence().get();
        if (maySharing == PresenceSharing.NONE) {
            cir.setReturnValue(PresenceStatus.OFFLINE);
        } else if (maySharing == PresenceSharing.LIMITED) {
            cir.setReturnValue(PresenceStatus.ONLINE);
        } else if (maySharing == PresenceSharing.ALL) {
            IntegratedServer server = this.minecraft.getSingleplayerServer();
            if (server == null) {
                cir.setReturnValue(PresenceStatus.ONLINE);
                return;
            }
            IntegratedServer.MultiplayerScope scope = server.getMultiplayerScope();
            if (scope == OFF || scope == LAN) {
                cir.setReturnValue(PresenceStatus.OFFLINE);
            } else if (scope == ONLINE || scope == ClientConstants.INTEGRATED_SERVER) {
                cir.setReturnValue(PresenceStatus.PLAYING_HOSTED_SERVER);
            } else {
                throw new MatchException(null, null);
            }
        } else {
            throw new MatchException(null, null);
        }
    }
}
