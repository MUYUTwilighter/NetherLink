package cool.muyucloud.netherlink.teacon.client;

import cool.muyucloud.netherlink.client.ClientFriendService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Confirmation dialog before sending a friend request via FriendCard. */
public class FriendCardFriendScreen extends ConfirmScreen {
    private static final Component TITLE = Component.literal("NetherLink");
    private static final Component CONFIRM = Component.translatable("gui.yes");
    private static final Component CANCEL = Component.translatable("gui.no");

    private final BlockPos clickedPos;
    private final Direction clickedFace;
    private final UUID targetId;

    public FriendCardFriendScreen(Component targetName, BlockPos clickedPos, UUID targetId, Direction clickedFace) {
        super(
            result -> { /* overridden in addButtons */ },
            TITLE,
            Component.translatable("screen.netherlink.friend_card.message", targetName),
            CONFIRM,
            CANCEL
        );
        this.clickedPos = clickedPos;
        this.clickedFace = clickedFace;
        this.targetId = targetId;
    }

    @Override
    public boolean shouldCloseOnEsc() { return true; }

    @Override
    protected void addButtons(LinearLayout buttonLayout) {
        this.yesButton = buttonLayout.addChild(
            Button.builder(this.yesButtonComponent, button -> {
                onConfirmed(true);
                this.onClose();
            }).width(80).build());
        this.noButton = buttonLayout.addChild(
            Button.builder(this.noButtonComponent, button -> this.onClose()).width(80).build());
    }

    private void onConfirmed(boolean confirmed) {
        if (!confirmed || targetId == null) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        String targetName = targetId.toString().substring(0, 8) + "...";
        try {
            var service = new ClientFriendService(minecraft);
            service.add(targetName).thenAccept(outcome -> {
                var msg = outcome.result() == cool.muyucloud.netherlink.link.model.LinkFriendActionResult.SUCCESS
                    ? Component.translatable("block.netherlink.friend_card.confirmed")
                    : Component.translatable("block.netherlink.friend_card.failed");
                minecraft.player.sendOverlayMessage(msg);
            });
        } catch (Exception e) {
            minecraft.player.sendOverlayMessage(
                Component.translatable("block.netherlink.friend_card.failed"));
        }
    }
}
