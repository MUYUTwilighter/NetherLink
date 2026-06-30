package cool.muyucloud.netherlink.mixin;

import cool.muyucloud.netherlink.client.ClientP2PController;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.MultiplayerOptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiplayerOptionsScreen.class)
public abstract class MultiplayerOptionsScreenMixin extends Screen {
    @Unique
    private boolean netherlink$initialFriendsOpen;

    @Unique
    private boolean netherlink$wantedFriendsOpen;

    protected MultiplayerOptionsScreenMixin(Component title) {
        super(title);
    }

    @Inject(
        method = "init",
        at = @At("TAIL")
    )
    private void netherlink$addFriendsNetworkButton(CallbackInfo ci) {
        IntegratedServer server = this.minecraft.getSingleplayerServer();
        boolean friendsOpen = server != null && ClientP2PController.isFriendsOpen(server);
        this.netherlink$initialFriendsOpen = friendsOpen;
        this.netherlink$wantedFriendsOpen = friendsOpen;

        this.addRenderableWidget(
            CycleButton.onOffBuilder(friendsOpen)
                .create(this.width / 2 - 155, this.height / 2 + 6, 310, 20, Component.translatable("netherlink.lan.friends"), (button, value) -> {
                    this.netherlink$wantedFriendsOpen = value;
                    IntegratedServer currentServer = this.minecraft.getSingleplayerServer();
                    if (currentServer != null) {
                        ClientP2PController.setFriendsOpen(this.minecraft, currentServer, value);
                    }
                })
        );
    }
}
