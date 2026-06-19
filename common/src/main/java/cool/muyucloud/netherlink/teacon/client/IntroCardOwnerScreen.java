package cool.muyucloud.netherlink.teacon.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import cool.muyucloud.netherlink.teacon.network.IntroCardActionPayload;

/** Owner's sign interaction menu: Edit, Clear, or Cancel. */
public class IntroCardOwnerScreen extends ConfirmScreen {
    private static final Component TITLE = Component.literal("NetherLink");
    private static final Component EDIT = Component.translatable("screen.netherlink.owner.edit");
    private static final Component CLEAR = Component.translatable("screen.netherlink.owner.clear");
    private static final Component CANCEL = Component.translatable("gui.cancel");

    private final BlockPos clickedPos;

    public IntroCardOwnerScreen(BlockPos clickedPos) {
        super(
            result -> { /* unused — we override buttons */ },
            TITLE,
            Component.translatable("screen.netherlink.owner.message"),
            CLEAR, // placeholder for yesButtonComponent
            CANCEL // placeholder for noButtonComponent
        );
        this.clickedPos = clickedPos;
    }

    @Override
    public boolean shouldCloseOnEsc() { return true; }

    @Override
    protected void addButtons(LinearLayout buttonLayout) {
        buttonLayout.addChild(Button.builder(EDIT, button -> {
            sendAction("edit");
            this.onClose();
        }).width(80).build());
        buttonLayout.addChild(Button.builder(CLEAR, button -> {
            sendAction("clear");
            this.onClose();
        }).width(80).build());
        buttonLayout.addChild(Button.builder(CANCEL, button -> this.onClose()).width(80).build());
    }

    private void sendAction(String action) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.connection.send(
                new ServerboundCustomPayloadPacket(
                    new IntroCardActionPayload(action, clickedPos)));
        }
    }
}
