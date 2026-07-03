package cool.muyucloud.netherlink.client;

import cool.muyucloud.netherlink.link.LinkService;
import cool.muyucloud.netherlink.link.exception.LinkFailures;
import cool.muyucloud.netherlink.link.model.LinkFriendSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

final class FriendNetworkSettingsRenderer implements LinkSettingsRenderer {
    private final LinkSettingsContext context;
    private final CycleButton<Boolean> friendsNetworkButton;
    private final CycleButton<Boolean> receiveRequestsButton;
    private @Nullable LinkFriendSettings remoteSettings;
    private boolean loadStarted;
    private boolean saving;

    FriendNetworkSettingsRenderer(LinkSettingsContext context) {
        this.context = context;
        this.friendsNetworkButton = CycleButton.onOffBuilder(false)
            .create(0, 0, 180, 20, Component.translatable("netherlink.friends.settings.network"), (ignored1, ignored2) -> this.changed());
        this.receiveRequestsButton = CycleButton.onOffBuilder(false)
            .create(0, 0, 180, 20, Component.translatable("netherlink.friends.settings.requests"), (ignored1, ignored2) -> this.changed());
        this.updateButtons();
    }

    @Override
    public Component title() {
        return this.context.service().name();
    }

    @Override
    public void visitChildren(Consumer<AbstractWidget> consumer) {
        consumer.accept(this.friendsNetworkButton);
        consumer.accept(this.receiveRequestsButton);
    }

    @Override
    public void doLayout(ScreenRectangle area) {
        int center = area.left() + area.width() / 2;
        this.friendsNetworkButton.setPosition(center - 90, area.top() + 12);
        this.receiveRequestsButton.setPosition(center - 90, area.top() + 36);
        if (!this.loadStarted) {
            this.load();
        }
    }

    @Override
    public void load() {
        if (this.loadStarted) {
            return;
        }
        this.loadStarted = true;
        if (!this.context.service().supports(LinkService.Capability.FRIEND_SETTINGS)) {
            this.updateButtons();
            return;
        }
        this.context.setStatus(Component.translatable("netherlink.friends.settings.loading").withStyle(ChatFormatting.GRAY));
        this.context.friendService().get().settings().whenComplete((settings, error) -> this.context.minecraft().execute(() -> {
            if (error != null) {
                this.context.setStatus(Component.translatable(
                    "netherlink.friends.settings.load_failed",
                    NetherLinkFriendsScreen.failureText(LinkFailures.from(error))
                ).withStyle(ChatFormatting.RED));
                this.updateButtons();
                return;
            }
            this.remoteSettings = settings;
            this.friendsNetworkButton.setValue(settings.friendsEnabled());
            this.receiveRequestsButton.setValue(settings.acceptInvites());
            this.updateButtons();
            this.context.setStatus(Component.translatable("netherlink.friends.settings.loaded").withStyle(ChatFormatting.GRAY));
        }));
    }

    @Override
    public boolean hasChanges() {
        return this.remoteSettings != null
            && (this.remoteSettings.friendsEnabled() != this.friendsNetworkButton.getValue()
            || this.remoteSettings.acceptInvites() != this.receiveRequestsButton.getValue());
    }

    @Override
    public boolean canApply() {
        return !this.saving
            && this.context.service().supports(LinkService.Capability.FRIEND_SETTINGS)
            && this.remoteSettings != null
            && this.hasChanges();
    }

    @Override
    public CompletableFuture<Boolean> apply() {
        if (!this.canApply()) {
            return CompletableFuture.completedFuture(false);
        }
        this.saving = true;
        this.updateButtons();
        LinkFriendSettings settings = new LinkFriendSettings(this.friendsNetworkButton.getValue(), this.receiveRequestsButton.getValue());
        this.context.setStatus(Component.translatable("netherlink.friends.settings.saving").withStyle(ChatFormatting.GRAY));

        CompletableFuture<Boolean> result = new CompletableFuture<>();
        this.context.friendService().get().updateSettings(settings).whenComplete((saved, error) -> this.context.minecraft().execute(() -> {
            this.saving = false;
            if (error != null) {
                this.context.setStatus(Component.translatable(
                    "netherlink.friends.settings.failed",
                    NetherLinkFriendsScreen.failureText(LinkFailures.from(error))
                ).withStyle(ChatFormatting.RED));
                this.updateButtons();
                result.complete(false);
                return;
            }
            this.remoteSettings = saved;
            this.friendsNetworkButton.setValue(saved.friendsEnabled());
            this.receiveRequestsButton.setValue(saved.acceptInvites());
            this.updateButtons();
            this.context.setStatus(Component.translatable("netherlink.friends.settings.saved").withStyle(ChatFormatting.GREEN));
            result.complete(true);
        }));
        return result;
    }

    private void changed() {
        if (!this.friendsNetworkButton.getValue()) {
            this.receiveRequestsButton.setValue(false);
        }
        this.updateButtons();
    }

    private void updateButtons() {
        boolean loaded = !this.saving
            && this.context.service().supports(LinkService.Capability.FRIEND_SETTINGS)
            && this.remoteSettings != null;
        this.friendsNetworkButton.active = loaded;
        this.receiveRequestsButton.active = loaded && this.friendsNetworkButton.getValue();
        this.context.updateApplyState().run();
    }
}
