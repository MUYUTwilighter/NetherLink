package cool.muyucloud.netherlink.client;

import cool.muyucloud.netherlink.link.exception.LinkFailures;
import cool.muyucloud.netherlink.link.model.LinkFailure;
import cool.muyucloud.netherlink.link.model.LinkJoinOperation;
import cool.muyucloud.netherlink.link.model.LinkJoinSnapshot;
import cool.muyucloud.netherlink.link.model.LinkJoinState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class NetherLinkJoinScreen extends Screen {
    private final Screen parent;
    private final UUID hostProfileId;
    private final String hostPresenceId;
    private final String friendName;
    private @Nullable CompletableFuture<LinkJoinOperation> operationFuture;
    private @Nullable LinkJoinOperation operation;
    private @Nullable Button cancelButton;
    private Component title = Component.translatable("netherlink.join.title", "");
    private Component status = Component.translatable("netherlink.join.preparing").withStyle(ChatFormatting.GRAY);
    private @Nullable Component detail;
    private boolean started;
    private boolean terminal;

    NetherLinkJoinScreen(Screen parent, UUID hostProfileId, String hostPresenceId, String friendName) {
        super(Component.translatable("netherlink.join.title", friendName));
        this.parent = parent;
        this.hostProfileId = hostProfileId;
        this.hostPresenceId = hostPresenceId;
        this.friendName = friendName;
    }

    @Override
    protected void init() {
        this.cancelButton = this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, ignored1 -> this.cancelOrClose())
            .bounds(this.width / 2 - 100, this.height / 2 + 60, 200, 20)
            .build());
        if (!this.started) {
            this.started = true;
            this.start();
        }
    }

    private void start() {
        this.status = Component.translatable("netherlink.join.opening_runtime").withStyle(ChatFormatting.GRAY);
        this.operationFuture = ClientJoinController.startJoin(
            Minecraft.getInstance(),
            this.hostProfileId,
            this.hostPresenceId,
            this,
            this::updateMinecraftStatus
        );
        this.operationFuture.whenComplete((result, error) -> this.minecraft.execute(() -> {
            if (this.terminal) {
                if (result != null) {
                    result.cancel();
                }
                return;
            }
            if (error != null) {
                this.fail(LinkFailures.from(error));
                return;
            }
            this.operation = result;
            this.operation.completion().whenComplete((ignored1, completionError) -> this.minecraft.execute(() -> {
                if (this.terminal) {
                    return;
                }
                if (completionError != null) {
                    this.fail(LinkFailures.from(completionError));
                    return;
                }
                this.status = Component.translatable("netherlink.join.minecraft.connecting").withStyle(ChatFormatting.GRAY);
                this.detail = Component.translatable("netherlink.join.minecraft.waiting").withStyle(ChatFormatting.DARK_GRAY);
                if (this.cancelButton != null) {
                    this.cancelButton.active = false;
                    this.cancelButton.visible = false;
                }
            }));
        }));
    }

    private void updateMinecraftStatus(Component status) {
        Minecraft.getInstance().execute(() -> {
            if (!this.terminal) {
                this.status = Component.translatable("netherlink.join.minecraft").withStyle(ChatFormatting.GRAY);
                this.detail = status.copy().withStyle(ChatFormatting.WHITE);
            }
        });
    }

    @Override
    public void tick() {
        super.tick();
        LinkJoinOperation current = this.operation;
        if (current != null && !current.completion().isDone()) {
            this.apply(current.snapshot());
        }
    }

    private void apply(LinkJoinSnapshot snapshot) {
        this.status = status(snapshot.state()).copy().withStyle(ChatFormatting.GRAY);
        this.detail = detail(snapshot);
        if (snapshot.state() == LinkJoinState.CONNECTED && this.cancelButton != null) {
            this.cancelButton.active = false;
            this.cancelButton.visible = false;
        }
    }

    private @Nullable Component detail(LinkJoinSnapshot snapshot) {
        if (snapshot.failure() != null) {
            return Component.literal(snapshot.failure().message()).withStyle(ChatFormatting.RED);
        }
        return switch (snapshot.state()) {
            case REQUESTING -> Component.translatable("netherlink.join.detail.requesting", this.friendName).withStyle(ChatFormatting.DARK_GRAY);
            case ACCEPTED -> Component.translatable("netherlink.join.detail.accepted").withStyle(ChatFormatting.DARK_GRAY);
            case NEGOTIATING -> Component.translatable("netherlink.join.detail.negotiating").withStyle(ChatFormatting.DARK_GRAY);
            case CONNECTING -> Component.translatable("netherlink.join.detail.connecting").withStyle(ChatFormatting.DARK_GRAY);
            case CONNECTED -> Component.translatable("netherlink.join.detail.connected").withStyle(ChatFormatting.DARK_GRAY);
            case REJECTED, FAILED, CANCELLED -> null;
        };
    }

    private static Component status(LinkJoinState state) {
        return switch (state) {
            case REQUESTING -> Component.translatable("netherlink.join.state.requesting");
            case ACCEPTED -> Component.translatable("netherlink.join.state.accepted");
            case NEGOTIATING -> Component.translatable("netherlink.join.state.negotiating");
            case CONNECTING -> Component.translatable("netherlink.join.state.connecting");
            case CONNECTED -> Component.translatable("netherlink.join.state.connected");
            case REJECTED -> Component.translatable("netherlink.join.state.rejected").withStyle(ChatFormatting.RED);
            case FAILED -> Component.translatable("netherlink.join.state.failed").withStyle(ChatFormatting.RED);
            case CANCELLED -> Component.translatable("netherlink.join.state.cancelled").withStyle(ChatFormatting.YELLOW);
        };
    }

    private void fail(LinkFailure failure) {
        this.terminal = true;
        this.status = Component.translatable("netherlink.join.state.failed").withStyle(ChatFormatting.RED);
        this.detail = Component.literal(failure.message()).withStyle(ChatFormatting.RED);
        if (this.cancelButton != null) {
            this.cancelButton.setMessage(CommonComponents.GUI_BACK);
            this.cancelButton.active = true;
            this.cancelButton.visible = true;
        }
    }

    private void cancelOrClose() {
        if (this.terminal) {
            this.minecraft.setScreen(this.parent);
            return;
        }
        LinkJoinOperation current = this.operation;
        if (current != null) {
            current.cancel();
        } else {
            ClientJoinController.shutdown();
        }
        this.terminal = true;
        this.status = Component.translatable("netherlink.join.state.cancelled").withStyle(ChatFormatting.YELLOW);
        this.detail = Component.translatable("netherlink.join.detail.cancelled").withStyle(ChatFormatting.DARK_GRAY);
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void onClose() {
        this.cancelOrClose();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    protected boolean shouldNarrateNavigation() {
        return false;
    }

    @Override
    public void render(@NonNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 58, -1);
        graphics.drawCenteredString(this.font, this.status, this.width / 2, this.height / 2 - 30, -1);
        if (this.detail != null) {
            graphics.drawCenteredString(this.font, this.detail, this.width / 2, this.height / 2 - 12, -1);
        }
    }
}
