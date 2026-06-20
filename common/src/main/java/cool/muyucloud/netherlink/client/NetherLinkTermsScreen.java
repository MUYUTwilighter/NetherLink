package cool.muyucloud.netherlink.client;

import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.link.LinkService;
import cool.muyucloud.netherlink.link.exception.LinkFailures;
import cool.muyucloud.netherlink.link.model.LinkTerms;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

final class NetherLinkTermsScreen extends Screen {
    private final Screen parent;
    private final LinkService service;
    private final Runnable action;
    private HeaderAndFooterLayout layout;
    private @Nullable ScrollableLayout scrollArea;
    private @Nullable LinkTerms terms;
    private @Nullable Component error;
    private boolean loading = true;
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
        this.scrollArea = null;
        this.layout = new HeaderAndFooterLayout(this);
        this.layout.addTitleHeader(this.title, this.font);

        if (this.loading) {
            this.addMessage(Component.translatable("netherlink.terms.loading").withStyle(ChatFormatting.GRAY));
            this.addBackButton();
        } else if (this.error != null) {
            this.addMessage(this.error.copy().withStyle(ChatFormatting.RED));
            LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
            footer.addChild(Button.builder(Component.translatable("netherlink.terms.retry"), _ -> this.retry()).width(100).build());
            footer.addChild(Button.builder(CommonComponents.GUI_BACK, _ -> this.onClose()).width(100).build());
        } else if (this.terms != null) {
            LinearLayout content = LinearLayout.vertical();
            content.addChild(new MultiLineTextWidget(Component.literal(this.terms.text()), this.font).setMaxWidth(310));
            this.scrollArea = new ScrollableLayout(this.minecraft, content, this.layout.getContentHeight());
            this.scrollArea.setMinWidth(310);
            this.layout.addToContents(this.scrollArea);
            LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
            footer.addChild(Button.builder(Component.translatable("netherlink.terms.accept"), _ -> this.accept()).width(100).build());
            footer.addChild(Button.builder(Component.translatable("netherlink.terms.decline"), _ -> this.onClose()).width(100).build());
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
        this.layout.addToFooter(Button.builder(CommonComponents.GUI_BACK, _ -> this.onClose()).width(200).build());
    }

    private void fetch() {
        String language = this.minecraft.getLanguageManager().getSelected().replace('_', '-');
        this.service.terms(language).whenComplete((result, failure) -> this.minecraft.execute(() -> {
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
            Optional<LinkTerms> fetched = result;
            if (fetched.isEmpty()) {
                this.continueAction();
                return;
            }
            this.terms = fetched.get();
            if (ClientTermsConsent.isAccepted(this.minecraft, this.service.id(), this.terms)) {
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
        if (this.terms == null) {
            return;
        }
        try {
            ClientTermsConsent.accept(this.minecraft, this.service.id(), this.terms);
            this.continueAction();
        } catch (RuntimeException failure) {
            NliConstants.LOG.warn("Failed to record accepted terms for {}", this.service.id(), failure);
            this.error = Component.translatable("netherlink.terms.save_error");
            this.terms = null;
            this.rebuildWidgets();
        }
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
        if (this.scrollArea != null) {
            this.scrollArea.arrangeElements();
            this.scrollArea.setMaxHeight(this.layout.getContentHeight());
        }
        this.layout.arrangeElements();
    }
}
