package cool.muyucloud.netherlink.mixin;

import cool.muyucloud.netherlink.client.NetherLinkFriendsScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
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
            Button.builder(Component.translatable("netherlink.friends.short"), button -> this.minecraft.setScreen(new NetherLinkFriendsScreen(this, true)))
                .bounds(this.width / 2 + 104, this.height / 4 + 72, 20, 20)
                .tooltip(Tooltip.create(Component.translatable("netherlink.friends.tooltip")))
                .build()
        );
    }
}
