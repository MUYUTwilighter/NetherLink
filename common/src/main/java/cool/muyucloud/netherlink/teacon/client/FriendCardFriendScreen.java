package cool.muyucloud.netherlink.teacon.client;

import cool.muyucloud.netherlink.client.ClientFriendService;
import cool.muyucloud.netherlink.link.exception.LinkFailures;
import cool.muyucloud.netherlink.link.model.LinkFailure;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Confirmation dialog before sending a friend request via FriendCard. */
public class FriendCardFriendScreen extends ConfirmScreen {
    private static final Component TITLE = Component.literal("NetherLink");
    private static final Component CONFIRM = Component.translatable("gui.yes");
    private static final Component CANCEL = Component.translatable("gui.no");

    private final @Nullable String targetName;

    public FriendCardFriendScreen(@Nullable String targetName, UUID targetId) {
        super(
            _ -> { /* overridden in addButtons */ },
            TITLE,
            Component.translatable(
                "screen.netherlink.friend_card.message",
                targetName != null && !targetName.isBlank() ? targetName : targetId.toString()
            ),
            CONFIRM,
            CANCEL
        );
        this.targetName = targetName;
    }

    @Override
    public boolean shouldCloseOnEsc() { return true; }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.isEscape()) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void addButtons(LinearLayout buttonLayout) {
        this.yesButton = buttonLayout.addChild(
            Button.builder(this.yesButtonComponent, _ -> {
                onConfirmed(true);
                this.onClose();
            }).width(80).build());
        this.noButton = buttonLayout.addChild(
            Button.builder(this.noButtonComponent, _ -> this.onClose()).width(80).build());
    }

    private void onConfirmed(boolean confirmed) {
        if (!confirmed) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        if (this.targetName == null || this.targetName.isBlank()) {
            minecraft.player.sendOverlayMessage(Component.translatable("block.netherlink.friend_card.unknown"));
            return;
        }
        try {
            new ClientFriendService(minecraft)
                .add(this.targetName)
                .whenComplete((outcome, err) -> minecraft.execute(() -> {
                    if (err != null) {
                        minecraft.player.sendOverlayMessage(failureMessage(LinkFailures.from(err)));
                        return;
                    }
                    if (outcome == null) {
                        minecraft.player.sendOverlayMessage(Component.translatable("block.netherlink.friend_card.failed"));
                        return;
                    }
                    var msg = switch (outcome.result()) {
                        case SUCCESS -> Component.translatable("block.netherlink.friend_card.confirmed");
                        case UNKNOWN_PROFILE -> Component.translatable("block.netherlink.friend_card.unknown");
                        case SERVICE_NOT_AVAILABLE -> Component.translatable("block.netherlink.friend_card.unavailable");
                        case TOO_MANY_REQUESTS -> Component.translatable("block.netherlink.friend_card.too_many");
                        case FORBIDDEN -> Component.translatable("block.netherlink.friend_card.forbidden");
                        default -> Component.translatable("block.netherlink.friend_card.failed");
                    };
                    minecraft.player.sendOverlayMessage(msg);
                }));
        } catch (Exception e) {
            minecraft.player.sendOverlayMessage(
                Component.translatable("block.netherlink.friend_card.failed"));
        }
    }

    private static Component failureMessage(LinkFailure failure) {
        return switch (failure.code()) {
            case UNAUTHORIZED -> Component.translatable("block.netherlink.friend_card.auth_failed");
            case PROFILE_NOT_FOUND -> Component.translatable("block.netherlink.friend_card.unknown");
            case SERVICE_UNAVAILABLE, NETWORK, TIMEOUT -> Component.translatable("block.netherlink.friend_card.unavailable");
            case RATE_LIMITED -> Component.translatable("block.netherlink.friend_card.too_many");
            case FORBIDDEN -> Component.translatable("block.netherlink.friend_card.forbidden");
            default -> Component.translatable("block.netherlink.friend_card.failed");
        };
    }
}
