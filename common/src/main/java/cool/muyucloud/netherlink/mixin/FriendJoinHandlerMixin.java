package cool.muyucloud.netherlink.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import cool.muyucloud.netherlink.client.ClientConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.p2p.FriendJoinHandler;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FriendJoinHandler.class)
public abstract class FriendJoinHandlerMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @ModifyExpressionValue(method = "handleJoinRequest", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/screens/social/PresenceHandler;isInvitedPmid(Ljava/util/UUID;)Z"))
    public boolean onHandlingJoinRequest(boolean original) {
        IntegratedServer server = this.minecraft.getSingleplayerServer();
        if (server == null) {
            return original;
        }
        return original || server.getMultiplayerScope() == ClientConstants.INTEGRATED_SERVER;
    }
}
