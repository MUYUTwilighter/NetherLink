package cool.muyucloud.netherlink.client;

import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.link.LinkService;
import cool.muyucloud.netherlink.link.exception.LinkFailures;
import cool.muyucloud.netherlink.link.model.LinkFriendSettings;
import cool.muyucloud.netherlink.link.model.LinkTerms;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.FittingMultiLineTextWidget;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

final class NetherLinkTermsScreen extends Screen {
    private final Screen parent;
    private final LinkService service;
    private final Runnable action;
    private HeaderAndFooterLayout layout;
    private @Nullable LinkTerms terms;
    private @Nullable LinkFriendSettings friendSettings;
    private @Nullable Component error;
    private boolean loading = true;
    private boolean accepting;
    private boolean requestStarted;
    private boolean completed;

    NetherLinkTermsScreen(Screen parent, LinkService service, Runnable action) {
        super(Component.translatable("netherlink.terms.title", service.name()));
        this.parent = parent;
        this.service = service;
        this.action = action;
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
            LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
            footer.addChild(Button.builder(Component.translatable("netherlink.terms.retry"), ignored1 -> this.retry()).width(100).build());
            footer.addChild(Button.builder(CommonComponents.GUI_BACK, ignored2 -> this.onClose()).width(100).build());
        } else if (this.terms != null) {
            this.layout.addToContents(new FittingMultiLineTextWidget(0, 0, 310, Math.max(80, this.height - 96), Component.literal(this.terms.text()), this.font));
            LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
            Button acceptButton = Button.builder(this.acceptButtonText(), ignored3 -> this.accept()).width(this.shouldEnableFriendNetwork() ? 160 : 100).build();
            acceptButton.active = !this.accepting;
            footer.addChild(acceptButton);
            footer.addChild(Button.builder(Component.translatable("netherlink.terms.decline"), ignored4 -> this.onClose()).width(100).build());
        }

        this.layout.visitWidgets(this::addRenderableWidget);
        this.repositionElements();
        if (!this.requestStarted) {
            this.requestStarted = true;
            this.fetch();
        }
    }

    private void addMessage(Component message) {
        LinearLayout content = this.layout.addToContents(LinearLayout.vertical());
        content.addChild(new MultiLineTextWidget(message, this.font).setMaxWidth(310).setCentered(true));
    }

    private void addBackButton() {
        this.layout.addToFooter(Button.builder(CommonComponents.GUI_BACK, ignored5 -> this.onClose()).width(200).build());
    }

    private void fetch() {
        String language = this.minecraft.getLanguageManager().getSelected().replace('_', '-');
        CompletableFuture<LinkFriendSettings> settingsFuture = this.service.supports(LinkService.Capability.FRIEND_SETTINGS)
            ? new ClientFriendService(this.minecraft).settings()
            : CompletableFuture.completedFuture(new LinkFriendSettings(true, true));
        this.service.terms(language).thenCombine(settingsFuture, TermsState::new).whenComplete((state, failure) -> this.minecraft.execute(() -> {
            if (this.completed) {
                return;
            }
            if (failure != null) {
                NliConstants.LOG.warn("Failed to fetch terms for {}", this.service.id(), failure);
                this.loading = false;
                this.error = Component.translatable(
                    "netherlink.terms.error",
                    LinkFailures.from(failure).message()
                );
                this.rebuildWidgets();
                return;
            }
            this.friendSettings = state.settings();
            Optional<LinkTerms> fetched = state.terms();
            if (fetched.isEmpty()) {
                this.continueAction();
                return;
            }
            this.terms = fetched.get();
            if (ClientTermsConsent.isAccepted(this.minecraft, this.service.id(), this.terms) && !this.shouldEnableFriendNetwork()) {
                this.continueAction();
                return;
            }
            this.loading = false;
            this.error = null;
            this.rebuildWidgets();
        }));
    }

    private void retry() {
        this.loading = true;
        this.error = null;
        this.terms = null;
        this.rebuildWidgets();
        this.fetch();
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
        new ClientFriendService(this.minecraft).updateSettings(new LinkFriendSettings(true, true))
            .whenComplete((saved, error) -> this.minecraft.execute(() -> {
                this.accepting = false;
                if (error != null) {
                    NliConstants.LOG.warn("Failed to enable friend network after accepting terms for {}", this.service.id(), error);
                    this.error = Component.translatable("netherlink.terms.enable_error", LinkFailures.from(error).message());
                    this.terms = null;
                    this.rebuildWidgets();
                    return;
                }
                ClientLinkSettings.updateMinecraftSocialManager(this.minecraft, saved);
                this.friendSettings = saved;
                this.recordAcceptanceAndContinue();
            }));
    }

    private void recordAcceptanceAndContinue() {
        LinkTerms acceptedTerms = this.terms;
        if (acceptedTerms == null) {
            return;
        }
        try {
            ClientTermsConsent.accept(this.minecraft, this.service.id(), acceptedTerms);
            this.continueAction();
        } catch (RuntimeException failure) {
            NliConstants.LOG.warn("Failed to record accepted terms for {}", this.service.id(), failure);
            this.error = Component.translatable("netherlink.terms.save_error");
            this.terms = null;
            this.rebuildWidgets();
        }
    }

    private Component acceptButtonText() {
        return this.shouldEnableFriendNetwork()
            ? Component.translatable("netherlink.terms.accept_enable_friends")
            : Component.translatable("netherlink.terms.accept");
    }

    private boolean shouldEnableFriendNetwork() {
        return this.friendSettings != null && !this.friendSettings.friendsEnabled();
    }

    private record TermsState(Optional<LinkTerms> terms, LinkFriendSettings settings) {
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
        this.minecraft.setScreen(this.parent);
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
    }
}
