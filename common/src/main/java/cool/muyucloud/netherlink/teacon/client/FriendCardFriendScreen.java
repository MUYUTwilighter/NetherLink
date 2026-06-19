package cool.muyucloud.netherlink.teacon.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/** Confirmation dialog before sending a friend request via FriendCard. */
public class FriendCardFriendScreen extends ConfirmScreen {
    private static final Component TITLE = Component.empty();
    private static final Component CONFIRM = Component.translatable("gui.yes");
    private static final Component CANCEL = Component.translatable("gui.no");

    private final BlockPos clickedPos;
    private final Direction clickedFace;

    public FriendCardFriendScreen(Component targetName, BlockPos clickedPos, Direction clickedFace) {
        super(
            result -> onConfirmed(result, clickedPos, clickedFace),
            TITLE,
            Component.translatable("screen.netherlink.friend_card.message", targetName),
            CONFIRM,
            CANCEL
        );
        this.clickedPos = clickedPos;
        this.clickedFace = clickedFace;
    }

    /** Allow ESC to close this screen. */
    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    protected void addButtons(LinearLayout buttonLayout) {
        this.yesButton = buttonLayout.addChild(
            Button.builder(this.yesButtonComponent, button -> {
                this.callback.accept(true);
                this.onClose();
            }).width(80).build());
        this.noButton = buttonLayout.addChild(
            Button.builder(this.noButtonComponent, button -> {
                this.callback.accept(false);
                this.onClose();
            }).width(80).build());
    }

    @Override
    protected void init() {
        this.closeButton = Button.builder(Component.literal("×"), button -> this.onClose())
            .bounds(this.width - 24, 6, 20, 20).build();
        super.init();
        addRenderableWidget(this.closeButton);
    }

    @Override
    protected void repositionElements() {
        super.repositionElements();
        if (this.closeButton != null) {
            this.closeButton.setPosition(this.width - 24, 6);
        }
    }

    private net.minecraft.client.gui.components.Button closeButton;

    private static void onConfirmed(boolean confirmed, BlockPos pos, Direction face) {
        if (confirmed) {
            // TODO: send friend request
        }
    }
}
