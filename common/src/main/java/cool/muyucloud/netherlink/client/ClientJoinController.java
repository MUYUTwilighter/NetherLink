package cool.muyucloud.netherlink.client;

import cool.muyucloud.netherlink.link.LinkServices;
import cool.muyucloud.netherlink.link.hook.LinkContextHooks;
import cool.muyucloud.netherlink.link.model.LinkJoinTarget;
import cool.muyucloud.netherlink.link.service.LinkRuntimeService;
import net.minecraft.client.Minecraft;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ClientJoinController {
    private ClientJoinController() {
    }

    public static CompletableFuture<Void> join(Minecraft minecraft, UUID hostProfileId, String hostPresenceId) {
        if (minecraft.level != null || minecraft.getSingleplayerServer() != null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Join requests are only available from the main menu"));
        }
        LauncherSessionAccount account = new LauncherSessionAccount(minecraft.getUser());
        LinkContextHooks.setClientConnection(
            LinkRuntimeService.CLIENT_KEY,
            account,
            "Minecraft Java instance",
            new MinecraftClientConnectionBridge(minecraft)
        );
        return LinkServices.current().runtime().open(LinkRuntimeService.CLIENT_KEY)
            .thenCompose(_ -> LinkServices.current().joining().join(
                LinkRuntimeService.CLIENT_KEY,
                new LinkJoinTarget(hostProfileId, hostPresenceId)
            ).completion());
    }

    public static boolean hasOutgoingJoin() {
        return LinkServices.current().joining().hasOutgoingJoin(LinkRuntimeService.CLIENT_KEY);
    }

    public static void shutdown() {
        LinkServices.current().joining().shutdown(LinkRuntimeService.CLIENT_KEY);
    }
}
