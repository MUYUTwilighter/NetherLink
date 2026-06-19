package cool.muyucloud.netherlink.teacon.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

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

    @Override
    protected void addButtons(LinearLayout buttonLayout) {
        this.yesButton = buttonLayout.addChild(
            Button.builder(this.yesButtonComponent, button -> this.callback.accept(true)).width(80).build());
        this.noButton = buttonLayout.addChild(
            Button.builder(this.noButtonComponent, button -> this.callback.accept(false)).width(80).build());
    }

    private static void onConfirmed(boolean confirmed, BlockPos pos, Direction face) {
        if (confirmed) {
            var minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.gameMode == null) return;
            var hitResult = new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false);
            minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hitResult);
        }
    }
}
