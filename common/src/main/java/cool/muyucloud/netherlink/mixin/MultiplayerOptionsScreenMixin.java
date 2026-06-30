package cool.muyucloud.netherlink.mixin;

import cool.muyucloud.netherlink.client.ClientP2PController;
import cool.muyucloud.netherlink.client.NetherLinkNetworkScope;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.screens.MultiplayerOptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiplayerOptionsScreen.class)
public abstract class MultiplayerOptionsScreenMixin extends Screen {
    @Shadow
    protected abstract void updateApplyChangesActiveState();

    @Shadow
    private MinecraftServer.MultiplayerScope wantedMultiplayerScope;

    @Shadow
    private MinecraftServer.MultiplayerScope initialMultiplayerScope;

    @Shadow
    private int port;

    @Shadow
    private void updatePortControlsState() {
        throw new AssertionError();
    }

    @Unique
    private NetherLinkNetworkScope netherlink$initialScope = NetherLinkNetworkScope.OFF;

    @Unique
    private NetherLinkNetworkScope netherlink$wantedScope = NetherLinkNetworkScope.OFF;

    protected MultiplayerOptionsScreenMixin(Component title) {
        super(title);
    }

    @ModifyArg(
        method = "init",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/layouts/LinearLayout;addChild(Lnet/minecraft/client/gui/layouts/LayoutElement;)Lnet/minecraft/client/gui/layouts/LayoutElement;",
            ordinal = 0
        )
    )
    private LayoutElement netherlink$replaceNetworkScopeButton(LayoutElement lanButton) {
        IntegratedServer server = this.minecraft.getSingleplayerServer();
        NetherLinkNetworkScope current = server == null ? NetherLinkNetworkScope.OFF : NetherLinkNetworkScope.current(server);
        this.netherlink$initialScope = current;
        this.netherlink$wantedScope = current;

        return CycleButton.builder(NetherLinkNetworkScope::displayName, current)
            .withValues(NetherLinkNetworkScope.values())
            .withTooltip(scope -> Tooltip.create(scope.tooltip()))
            .create(Component.translatable("menu.multiplayerOptions.network"), (button, value) -> {
                this.netherlink$wantedScope = value;
                this.netherlink$syncWantedVanillaScope();
                this.updatePortControlsState();
                this.updateApplyChangesActiveState();
            });
    }

    @Inject(method = "hasSettingsChanges", at = @At("RETURN"), cancellable = true)
    private void netherlink$hasFriendsNetworkChanges(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(cir.getReturnValueZ() || this.netherlink$wantedScope != this.netherlink$initialScope);
    }

    @ModifyArg(
        method = "init",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/Button;builder(Lnet/minecraft/network/chat/Component;Lnet/minecraft/client/gui/components/Button$OnPress;)Lnet/minecraft/client/gui/components/Button$Builder;",
            ordinal = 0
        ),
        index = 1
    )
    private Button.OnPress netherlink$applyFriendsNetwork(Button.OnPress original) {
        return button -> {
            boolean changed = this.netherlink$wantedScope != this.netherlink$initialScope;
            NetherLinkNetworkScope initial = this.netherlink$initialScope;
            NetherLinkNetworkScope wanted = this.netherlink$wantedScope;
            original.onPress(button);
            if (changed) {
                IntegratedServer server = this.minecraft.getSingleplayerServer();
                if (server != null) {
                    if (wanted == NetherLinkNetworkScope.NETHERLINK) {
                        ClientP2PController.setFriendsOpen(this.minecraft, server, true, this.port);
                    } else if (initial == NetherLinkNetworkScope.NETHERLINK && wanted == NetherLinkNetworkScope.OFF) {
                        ClientP2PController.setFriendsOpen(this.minecraft, server, false, this.port);
                    }
                }
            }
        };
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void netherlink$syncApplyButtonState(CallbackInfo ci) {
        IntegratedServer server = this.minecraft.getSingleplayerServer();
        if (server != null) {
            this.netherlink$initialScope = NetherLinkNetworkScope.current(server);
            this.netherlink$wantedScope = this.netherlink$initialScope;
            this.initialMultiplayerScope = this.netherlink$initialScope.vanillaScope();
            this.netherlink$syncWantedVanillaScope();
            this.updatePortControlsState();
        }
        this.updateApplyChangesActiveState();
    }

    @Unique
    private void netherlink$syncWantedVanillaScope() {
        this.wantedMultiplayerScope = this.netherlink$wantedScope.vanillaScope();
    }
}
