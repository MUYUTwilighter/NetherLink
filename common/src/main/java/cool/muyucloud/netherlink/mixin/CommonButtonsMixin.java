package cool.muyucloud.netherlink.mixin;

import cool.muyucloud.netherlink.client.ClientTermsController;
import cool.muyucloud.netherlink.client.NetherLinkFriendsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CommonButtons;
import net.minecraft.client.gui.components.FriendsButton;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CommonButtons.class)
public abstract class CommonButtonsMixin {
    @Inject(method = "friends", at = @At("HEAD"), cancellable = true)
    private static void netherlink$openNetherLinkFriends(int width, Button.OnPress onPress, boolean friendsAvailable, CallbackInfoReturnable<FriendsButton> cir) {
        cir.setReturnValue(new FriendsButton(width, _ -> {
            Minecraft minecraft = Minecraft.getInstance();
            Screen parent = minecraft.gui.screen();
            ClientTermsController.runAfterAcceptance(
                minecraft,
                parent,
                () -> minecraft.gui.setScreen(new NetherLinkFriendsScreen(parent, parent == null))
            );
        }, friendsAvailable));
    }
}
