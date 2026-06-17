package cool.muyucloud.netherlink.link.official;

import cool.muyucloud.netherlink.account.MinecraftAccount;
import cool.muyucloud.netherlink.account.NetherLinkAuthException;
import cool.muyucloud.netherlink.link.LinkHostContext;
import cool.muyucloud.netherlink.link.LinkHostPublication;
import cool.muyucloud.netherlink.link.LinkHostingService;
import cool.muyucloud.netherlink.p2p.ServerP2PManager;
import cool.muyucloud.netherlink.p2p.SignalingClient;
import net.minecraft.server.MinecraftServer;

import java.time.Duration;
import java.util.concurrent.CompletionException;

public final class OfficialHostingService implements LinkHostingService {
    private final OfficialPresenceService presence;

    public OfficialHostingService(OfficialPresenceService presence) {
        this.presence = presence;
    }

    @Override
    public LinkHostPublication publish(LinkHostContext context, Duration signalingReadyTimeout) {
        MinecraftAccount account = context.account();
        ServerP2PManager manager = new ServerP2PManager(
            context.accountName(),
            account,
            context.server(),
            new SignalingClient(account.getMcToken(), "NetherLink Signaling-" + context.accountName())
        );
        manager.start();
        try {
            awaitSignalingReady(manager, signalingReadyTimeout);
            Publication publication = new Publication(account, context.server(), manager, this.presence);
            publication.refresh();
            return publication;
        } catch (RuntimeException e) {
            manager.shutdown();
            throw e;
        }
    }

    private static void awaitSignalingReady(ServerP2PManager manager, Duration timeout) {
        try {
            manager.awaitSignalingReady(timeout).join();
        } catch (CompletionException e) {
            throw new NetherLinkAuthException("Signaling did not become ready before publishing presence", e);
        }
    }

    private record Publication(MinecraftAccount account, MinecraftServer server, ServerP2PManager manager, OfficialPresenceService presence) implements LinkHostPublication {
        @Override
        public void refresh() {
            this.manager.updatePresence(this.presence.publish(this.account));
        }

        @Override
        public void revoke() {
            try {
                this.presence.revoke(this.account);
            } finally {
                this.manager.shutdown();
            }
        }
    }
}
