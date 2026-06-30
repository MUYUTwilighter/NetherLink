package cool.muyucloud.netherlink.client;

import cool.muyucloud.netherlink.link.LinkServices;
import cool.muyucloud.netherlink.link.hook.LinkContextHooks;
import cool.muyucloud.netherlink.link.model.LinkJoinOperation;
import cool.muyucloud.netherlink.link.model.LinkJoinTarget;
import cool.muyucloud.netherlink.link.service.LinkRuntimeService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class ClientJoinController {
    private ClientJoinController() {
    }

    public static CompletableFuture<Void> join(Minecraft minecraft, UUID hostProfileId, String hostPresenceId) {
        return startJoin(minecraft, hostProfileId, hostPresenceId, new ProgressScreen(true), _ -> {
        }).thenCompose(LinkJoinOperation::completion);
    }

    public static CompletableFuture<LinkJoinOperation> startJoin(
        Minecraft minecraft,
        UUID hostProfileId,
        String hostPresenceId,
        Screen progressScreen,
        Consumer<Component> statusChanged
    ) {
        if (minecraft.level != null || minecraft.getSingleplayerServer() != null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Join requests are only available from the main menu"));
        }
        LauncherSessionAccount account = new LauncherSessionAccount(minecraft.getUser());
        LinkContextHooks.setClientConnection(
            LinkRuntimeService.CLIENT_KEY,
            account,
            "Minecraft Java instance",
            new MinecraftClientConnectionBridge(minecraft, progressScreen, statusChanged)
        );
        return LinkServices.current().runtime().open(LinkRuntimeService.CLIENT_KEY)
            .thenApply(_ -> LinkServices.current().joining().join(
                LinkRuntimeService.CLIENT_KEY,
                new LinkJoinTarget(hostProfileId, hostPresenceId)
            ));
    }

    public static boolean hasOutgoingJoin() {
        return LinkServices.current().joining().hasOutgoingJoin(LinkRuntimeService.CLIENT_KEY);
    }

    public static void shutdown() {
        LinkServices.current().joining().shutdown(LinkRuntimeService.CLIENT_KEY);
    }
}
