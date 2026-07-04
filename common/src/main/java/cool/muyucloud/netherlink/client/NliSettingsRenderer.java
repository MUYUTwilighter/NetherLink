package cool.muyucloud.netherlink.client;

import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.link.hook.LinkContextHooks;
import cool.muyucloud.netherlink.link.service.LinkRuntimeService;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.navigation.ScreenRectangle;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

final class NliSettingsRenderer implements LinkSettingsRenderer {
    private final LinkSettingsContext context;
    private final FriendNetworkSettingsRenderer delegate;

    NliSettingsRenderer(LinkSettingsContext context) {
        this.context = context;
        this.delegate = new FriendNetworkSettingsRenderer(context);
    }

    @Override
    public void visitChildren(Consumer<AbstractWidget> consumer) {
        this.delegate.visitChildren(consumer);
    }

    @Override
    public void doLayout(ScreenRectangle area) {
        this.delegate.doLayout(area);
    }

    @Override
    public void load() {
        this.delegate.load();
    }

    @Override
    public boolean hasChanges() {
        return this.delegate.hasChanges();
    }

    @Override
    public boolean canApply() {
        return this.delegate.canApply();
    }

    @Override
    public CompletableFuture<Boolean> apply() {
        return this.delegate.apply()
            .thenCompose(refresh -> this.publishPresence().thenApply(ignored -> refresh));
    }

    private CompletableFuture<Boolean> publishPresence() {
        return ClientP2PController.refreshPresence(this.context.minecraft())
            .thenCompose(hosting -> hosting
                ? CompletableFuture.completedFuture(false)
                : this.publishOnlinePresence());
    }

    private CompletableFuture<Boolean> publishOnlinePresence() {
        LauncherSessionAccount account = new LauncherSessionAccount(this.context.minecraft().getUser());
        if (!account.isUsable()) {
            NliConstants.LOG.debug("Skipping NLI settings presence refresh because the launcher account has no Minecraft access token");
            return CompletableFuture.completedFuture(false);
        }
        LinkContextHooks.setClientConnection(
            LinkRuntimeService.CLIENT_KEY,
            account,
            NliConstants.resolveInstanceName(),
            new MinecraftClientConnectionBridge(this.context.minecraft())
        );
        return this.context.service().runtime().publishOnline(LinkRuntimeService.CLIENT_KEY)
            .thenApply(ignored -> false);
    }
}
