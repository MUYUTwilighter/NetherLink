package cool.muyucloud.netherlink.mixin;

import cool.muyucloud.netherlink.client.ClientP2PController;
import cool.muyucloud.netherlink.client.NetherLinkFriendsScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "createPauseMenu", at = @At("TAIL"))
    private void onCreatePauseMenu(CallbackInfo ci) {
        IntegratedServer server = this.minecraft.getSingleplayerServer();
        int y = this.height / 4 + 152;
        if (server != null && server.isPublished()) {
            this.addRenderableWidget(Button.builder(label(server), button -> {
                boolean open = !ClientP2PController.isFriendsOpen(server);
                ClientP2PController.setFriendsOpen(this.minecraft, server, open);
                button.setMessage(label(server));
            }).bounds(this.width / 2 - 102, y, 204, 20).build());
            y += 24;
        }
        this.addRenderableWidget(
            Button.builder(Component.translatable("netherlink.friends.title"), button -> this.minecraft.setScreen(new NetherLinkFriendsScreen(this, false)))
                .bounds(this.width / 2 - 102, y, 204, 20)
                .build()
        );
    }

    private static Component label(IntegratedServer server) {
        return Component.translatable(ClientP2PController.isFriendsOpen(server) ? "netherlink.lan.friends.close" : "netherlink.lan.friends.open");
    }
}
