package cool.muyucloud.netherlink.mixin;

import cool.muyucloud.netherlink.client.ClientLanSettings;
import cool.muyucloud.netherlink.client.ClientP2PController;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShareToLanScreen.class)
public abstract class ShareToLanScreenMixin extends Screen {
    protected ShareToLanScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        IntegratedServer server = this.minecraft.getSingleplayerServer();
        boolean friendsOpen = server != null && ClientP2PController.isFriendsOpen(server);
        ClientLanSettings.setFriendsOpen(friendsOpen);
        int friendsButtonY = Math.min(184, this.height - 52);
        this.addRenderableWidget(
            CycleButton.onOffBuilder(friendsOpen)
                .create(
                    this.width / 2 - 155,
                    friendsButtonY,
                    310,
                    20,
                    Component.translatable("netherlink.lan.friends"),
                    (button, value) -> ClientLanSettings.setFriendsOpen(value)
                )
        );
    }
}
