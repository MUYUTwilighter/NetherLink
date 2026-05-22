package cool.muyucloud.netherlink.mixin;

import cool.muyucloud.netherlink.client.ClientP2PController;
import cool.muyucloud.netherlink.client.NetherLinkFriendsScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
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
        IntegratedServer server = this.minecraft.getSingleplayerServer();
        int rowY = this.height / 4 + 80;
        this.addRenderableWidget(
            Button.builder(Component.translatable("netherlink.friends.short"), _ -> this.minecraft.setScreen(new NetherLinkFriendsScreen(this, false)))
                .bounds(this.width / 2 + 106, rowY, 20, 20)
                .tooltip(Tooltip.create(Component.translatable("netherlink.friends.tooltip")))
                .build()
        );
        if (server != null && server.isPublished()) {
            this.addRenderableWidget(Button.builder(nli$toggleLabel(server), button -> {
                boolean open = !ClientP2PController.isFriendsOpen(server);
                ClientP2PController.setFriendsOpen(this.minecraft, server, open);
                button.setMessage(nli$toggleLabel(server));
                button.setTooltip(Tooltip.create(nli$label(server)));
            }).bounds(this.width / 2 + 130, rowY, 44, 20).tooltip(Tooltip.create(nli$label(server))).build());
        }
    }

    @Unique
    private static Component nli$label(IntegratedServer server) {
        return Component.translatable(ClientP2PController.isFriendsOpen(server) ? "netherlink.lan.friends.close" : "netherlink.lan.friends.open");
    }

    @Unique
    private static Component nli$toggleLabel(IntegratedServer server) {
        return ClientP2PController.isFriendsOpen(server) ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF;
    }
}
