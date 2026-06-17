package cool.muyucloud.netherlink.client;

import cool.muyucloud.netherlink.link.LinkServices;
import cool.muyucloud.netherlink.link.hook.LinkClientHooks;
import cool.muyucloud.netherlink.link.model.LinkFriendEntry;
import cool.muyucloud.netherlink.link.model.LinkFriendRelationship;
import net.minecraft.client.Minecraft;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ClientJoinController {
    private ClientJoinController() {
    }

    public static CompletableFuture<Void> join(Minecraft minecraft, UUID hostProfileId, UUID hostPresenceId) {
        LauncherSessionAccount account = new LauncherSessionAccount(minecraft.getUser());
        LinkClientHooks.setClient(minecraft, account);
        LinkFriendEntry target = new LinkFriendEntry(hostProfileId, "", hostPresenceId, LinkFriendRelationship.FRIEND, "PLAYING_HOSTED_SERVER", true);
        return LinkServices.current().joining().join(target);
    }

    public static boolean hasOutgoingJoin() {
        return LinkServices.current().joining().hasOutgoingJoin();
    }

    public static void shutdown() {
        LinkServices.current().joining().shutdown();
    }
}
