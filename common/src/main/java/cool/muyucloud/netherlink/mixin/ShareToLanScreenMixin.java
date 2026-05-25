package cool.muyucloud.netherlink.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.client.ClientLanSettings;
import cool.muyucloud.netherlink.client.ClientP2PController;
import net.minecraft.client.gui.components.Button;
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

    @WrapOperation(
        method = "init",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/Button;builder(Lnet/minecraft/network/chat/Component;Lnet/minecraft/client/gui/components/Button$OnPress;)Lnet/minecraft/client/gui/components/Button$Builder;"
        )
    )
    private Button.Builder onButtonBuilder(Component message, Button.OnPress onPress, Operation<Button.Builder> original) {
        if (!Component.translatable("lanServer.start").getString().equals(message.getString())) {
            return original.call(message, onPress);
        }
        Button.OnPress wrapped = button -> {
            onPress.onPress(button);
            IntegratedServer server = this.minecraft.getSingleplayerServer();
            boolean published = server != null && server.isPublished();
            NliConstants.LOG.info(
                "[P2P][client] ShareToLanScreen start button pressed published={} friendsOpen={}",
                published,
                ClientLanSettings.friendsOpen()
            );
            if (published) {
                ClientP2PController.setFriendsOpen(this.minecraft, server, ClientLanSettings.friendsOpen());
            }
        };
        return original.call(message, wrapped);
    }
}
