package cool.muyucloud.netherlink.mixin;

import cool.muyucloud.netherlink.client.ClientTermsController;
import cool.muyucloud.netherlink.client.NetherLinkFriendsScreen;
import cool.muyucloud.netherlink.client.NetherLinkIconButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        this.addRenderableWidget(
            new NetherLinkIconButton(this.width / 2 + 104, this.height / 4 + 72, ignored -> {
                    assert this.minecraft != null;
                    ClientTermsController.runAfterAcceptance(
                        this.minecraft,
                        this,
                        () -> this.minecraft.setScreen(new NetherLinkFriendsScreen(this, true))
                    );
                }
            )
        );
    }
}