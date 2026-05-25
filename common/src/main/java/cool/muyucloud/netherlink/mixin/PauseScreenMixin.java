package cool.muyucloud.netherlink.mixin;

import cool.muyucloud.netherlink.client.ClientP2PController;
import cool.muyucloud.netherlink.client.NetherLinkFriendsScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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
        assert this.minecraft != null;
        IntegratedServer server = this.minecraft.getSingleplayerServer();
        int rowY = this.height / 4 + 80;
        this.addRenderableWidget(
            new Button(
                this.width / 2 + 106,
                rowY,
                20,
                20,
                new net.minecraft.network.chat.TranslatableComponent("netherlink.friends.short"),
                ignored -> this.minecraft.setScreen(new NetherLinkFriendsScreen(this, false))
            )
        );
        if (server != null && server.isPublished()) {
            this.addRenderableWidget(new Button(this.width / 2 + 130, rowY, 44, 20, nli$toggleLabel(server), button -> {
                boolean open = !ClientP2PController.isFriendsOpen(server);
                ClientP2PController.setFriendsOpen(this.minecraft, server, open);
                button.setMessage(nli$toggleLabel(server));
            }));
        }
    }

    @Unique
    private static Component nli$toggleLabel(IntegratedServer server) {
        return ClientP2PController.isFriendsOpen(server) ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF;
    }
}
