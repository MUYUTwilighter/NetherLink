package cool.muyucloud.netherlink.client;

import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.link.LinkService;
import cool.muyucloud.netherlink.link.LinkServices;
import cool.muyucloud.netherlink.link.exception.LinkFailures;
import cool.muyucloud.netherlink.link.model.LinkFriendSettings;
import cool.muyucloud.netherlink.link.model.LinkTerms;
import cool.muyucloud.netherlink.link.model.LinkTermsState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.FittingMultiLineTextWidget;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

final class NetherLinkTermsScreen extends Screen {
    private final Screen parent;
    private final LinkService service;
    private final Runnable action;
    private HeaderAndFooterLayout layout;
    private @Nullable LinkTerms terms;
    private @Nullable LinkFriendSettings friendSettings;
    private @Nullable Component error;
    private boolean loading;
    private boolean accepting;
    private boolean requestStarted;
    private boolean settingsRequestStarted;
    private boolean completed;

    NetherLinkTermsScreen(Screen parent, LinkService service, Runnable action) {
        super(Component.translatable("netherlink.terms.title", service.name()));
        this.parent = parent;
        this.service = service;
        this.action = action;
        this.loading = true;
    }

    NetherLinkTermsScreen(Screen parent, LinkService service, Runnable action, LinkTermsState initialState) {
        super(Component.translatable("netherlink.terms.title", service.name()));
        this.parent = parent;
        this.service = service;
        this.action = action;
        this.loading = false;
        this.requestStarted = true;
        this.applyTermsState(initialState);
    }

    @Override
    protected void init() {
        this.layout = new HeaderAndFooterLayout(this);
        this.layout.addToHeader(new StringWidget(this.title, this.font));

        if (this.loading) {
            this.addMessage(Component.translatable("netherlink.terms.loading").withStyle(ChatFormatting.GRAY));
            this.addBackButton();
        } else if (this.error != null) {
            this.addMessage(this.error.copy().withStyle(ChatFormatting.RED));
            LinearLayout footer = this.layout.addToFooter(new LinearLayout(0, 0, LinearLayout.Orientation.HORIZONTAL));
            footer.defaultChildLayoutSetting().paddingHorizontal(4);
            footer.addChild(Button.builder(Component.translatable("netherlink.terms.retry"), ignored1 -> this.retry()).width(100).build());
            footer.addChild(Button.builder(CommonComponents.GUI_BACK, ignored2 -> this.onClose()).width(100).build());
        } else if (this.terms != null) {
            this.layout.addToContents(new FittingMultiLineTextWidget(0, 0, 310, Math.max(80, this.height - 96), Component.literal(this.terms.text()), this.font));
            LinearLayout footer = this.layout.addToFooter(new LinearLayout(0, 0, LinearLayout.Orientation.HORIZONTAL));
            footer.defaultChildLayoutSetting().paddingHorizontal(4);
            Button acceptButton = Button.builder(this.acceptButtonText(), ignored3 -> this.accept()).width(this.shouldEnableFriendNetwork() ? 160 : 100).build();
            acceptButton.active = !this.accepting;
            footer.addChild(acceptButton);
            footer.addChild(Button.builder(Component.translatable("netherlink.terms.decline"), ignored4 -> this.onClose()).width(100).build());
        } else {
            this.continueAction();
            return;
        }

        this.layout.visitWidgets(this::addRenderableWidget);
        this.repositionElements();
        if (!this.requestStarted) {
            this.requestStarted = true;
            this.fetch(false);
        }
        this.loadFriendSettingsIfNeeded();
    }

    private void addMessage(Component message) {
        LinearLayout content = this.layout.addToContents(new LinearLayout(0, 0, LinearLayout.Orientation.VERTICAL));
        content.addChild(new MultiLineTextWidget(message, this.font).setMaxWidth(310).setCentered(true));
    }

    private void addBackButton() {
        this.layout.addToFooter(Button.builder(CommonComponents.GUI_BACK, ignored5 -> this.onClose()).width(200).build());
    }

    private void fetch(boolean refreshTerms) {
        CompletableFuture<LinkTermsState> termsFuture = refreshTerms
            ? ClientTermsController.refreshTermsState(this.minecraft, this.service)
            : ClientTermsController.termsState(this.minecraft, this.service);
        termsFuture.whenComplete((state, failure) -> this.minecraft.execute(() -> {
            if (this.completed) {
                return;
            }
            if (failure != null) {
                NliConstants.LOG.warn("Failed to fetch terms gate state for {}", this.service.id(), failure);
                this.loading = false;
                this.error = errorMessage(failure);
                this.rebuildWidgets();
                return;
            }
            this.applyTermsState(state);
            if (this.error != null) {
                this.loading = false;
                this.rebuildWidgets();
                return;
            }
            if (this.terms == null) {
                this.continueAction();
                return;
            }
            if (state.isAccepted()) {
                this.continueAction();
                return;
            }
            this.loading = false;
            this.error = null;
            this.rebuildWidgets();
            this.loadFriendSettingsIfNeeded();
        }));
    }

    private void loadFriendSettingsIfNeeded() {
        if (this.terms == null || this.friendSettings != null || this.settingsRequestStarted) {
            return;
        }
        this.settingsRequestStarted = true;
        ClientTermsController.friendSettings(this.minecraft, this.service)
            .whenComplete((settings, failure) -> this.minecraft.execute(() -> {
                if (this.completed) {
                    return;
                }
                if (failure != null) {
                    NliConstants.LOG.warn("Failed to fetch friend settings while showing terms for {}", this.service.id(), failure);
                    return;
                }
                this.friendSettings = settings;
                this.rebuildWidgets();
            }));
    }

    private void retry() {
        this.loading = true;
        this.error = null;
        this.terms = null;
        this.friendSettings = null;
        this.settingsRequestStarted = false;
        this.rebuildWidgets();
        this.fetch(true);
    }

    private void accept() {
        if (this.terms == null || this.accepting) {
            return;
        }
        if (!this.shouldEnableFriendNetwork()) {
            this.recordAcceptanceAndContinue();
            return;
        }
        this.accepting = true;
        this.rebuildWidgets();
        new ClientFriendService(this.minecraft, this.service).updateSettings(new LinkFriendSettings(true, true))
            .whenComplete((saved, error) -> this.minecraft.execute(() -> {
                this.accepting = false;
                if (error != null) {
                    NliConstants.LOG.warn("Failed to enable friend network after accepting terms for {}", this.service.id(), error);
                    this.error = Component.translatable("netherlink.terms.enable_error", LinkFailures.from(error).message());
                    this.terms = null;
                    this.rebuildWidgets();
                    return;
                }
                this.friendSettings = saved;
                this.recordAcceptanceAndContinue();
            }));
    }

    private void recordAcceptanceAndContinue() {
        LinkTerms acceptedTerms = this.terms;
        if (acceptedTerms == null) {
            return;
        }
        this.accepting = true;
        this.rebuildWidgets();
        this.service.acceptTerms(acceptedTerms).whenComplete((ignored, failure) -> this.minecraft.execute(() -> {
            this.accepting = false;
            if (failure == null) {
                ClientTermsController.markAccepted(this.minecraft, this.service, acceptedTerms);
                this.continueAction();
                return;
            }
            NliConstants.LOG.warn("Failed to record accepted terms for {}", this.service.id(), failure);
            this.error = Component.translatable("netherlink.terms.save_error");
            this.terms = null;
            this.rebuildWidgets();
        }));
    }

    private Component acceptButtonText() {
        return this.shouldEnableFriendNetwork()
            ? Component.translatable("netherlink.terms.accept_enable_friends")
            : Component.translatable("netherlink.terms.accept");
    }

    private boolean shouldEnableFriendNetwork() {
        return this.friendSettings != null && !this.friendSettings.friendsEnabled();
    }

    private void applyTermsState(LinkTermsState state) {
        if (state.status().needsPrompt()) {
            this.terms = state.terms().orElse(null);
            this.error = state.failure().map(NetherLinkTermsScreen::errorMessage).orElse(null);
            return;
        }
        this.terms = state.terms().orElse(null);
        this.error = null;
    }

    private static Component errorMessage(Throwable failure) {
        return Component.translatable("netherlink.terms.error", LinkFailures.from(failure).message());
    }

    private void continueAction() {
        if (this.completed) {
            return;
        }
        this.completed = true;
        this.action.run();
        if (this.minecraft.screen == this) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void onClose() {
        this.completed = true;
        this.closeInactiveService();
        this.minecraft.setScreen(this.parent);
    }

    private void closeInactiveService() {
        try {
            if (LinkServices.current() != this.service) {
                this.service.shutdown().exceptionally(error -> {
                    NliConstants.LOG.warn("Failed to close inactive terms backend {}", this.service.id(), error);
                    return null;
                });
            }
        } catch (RuntimeException ignored) {
            // A service transition is already in progress; the transition owner is responsible for cleanup.
        }
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
