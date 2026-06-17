package cool.muyucloud.netherlink.client;

import cool.muyucloud.netherlink.link.LinkClientContext;
import cool.muyucloud.netherlink.link.LinkFriendEntry;
import cool.muyucloud.netherlink.link.LinkFriendRelationship;
import cool.muyucloud.netherlink.link.LinkServices;
import net.minecraft.client.Minecraft;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ClientJoinController {
    private ClientJoinController() {
    }

    public static CompletableFuture<Void> join(Minecraft minecraft, UUID hostProfileId, UUID hostPresenceId) {
        LauncherSessionAccount account = new LauncherSessionAccount(minecraft.getUser());
        LinkFriendEntry target = new LinkFriendEntry(hostProfileId, "", hostPresenceId, LinkFriendRelationship.FRIEND, "PLAYING_HOSTED_SERVER", true);
        return LinkServices.current().joining().join(new LinkClientContext() {
            @Override
            public LauncherSessionAccount account() {
                return account;
            }

            @Override
            public Minecraft minecraft() {
                return minecraft;
            }
        }, target);
    }

    public static boolean hasOutgoingJoin() {
        return LinkServices.current().joining().hasOutgoingJoin();
    }

    public static void shutdown() {
        LinkServices.current().joining().shutdown();
    }
}
