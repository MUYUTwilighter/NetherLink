package cool.muyucloud.netherlink.client;

import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.account.NetherLinkAuthException;
import cool.muyucloud.netherlink.account.PresencePublisher;
import cool.muyucloud.netherlink.p2p.ServerP2PManager;
import cool.muyucloud.netherlink.p2p.SignalingException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;

import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ClientP2PController {
    private static final Duration SIGNALING_READY_TIMEOUT = Duration.ofSeconds(15L);
    private static final PresencePublisher PRESENCE = new PresencePublisher();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "NetherLink Client P2P");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean PENDING = new AtomicBoolean();
    private static ServerP2PManager manager;
    private static LauncherSessionAccount account;

    private ClientP2PController() {
    }

    public static void setFriendsOpen(Minecraft minecraft, IntegratedServer integratedServer, boolean open) {
        ((NetherLinkIntegratedServer)integratedServer).nli$setFriendsOpen(open);
        if (open) {
            publish(minecraft, integratedServer);
        } else {
            revoke(minecraft);
        }
    }

    public static void publish(Minecraft minecraft, IntegratedServer integratedServer) {
        if (!integratedServer.isPublished()) {
            return;
        }
        if (!PENDING.compareAndSet(false, true)) {
            return;
        }
        EXECUTOR.execute(() -> {
            try {
                LauncherSessionAccount sessionAccount = new LauncherSessionAccount(minecraft.getUser());
                if (!sessionAccount.isUsable()) {
                    ((NetherLinkIntegratedServer)integratedServer).nli$setFriendsOpen(false);
                    NliConstants.LOG.warn("Launcher account is missing Minecraft token or PMID; NetherLink friends access is unavailable");
                    message(minecraft, new net.minecraft.network.chat.TranslatableComponent("netherlink.client.friends.unavailable"));
                    return;
                }
                stopManager();
                account = sessionAccount;
                manager = new ServerP2PManager("launcher:" + sessionAccount.getMcProfileName(), sessionAccount, integratedServer);
                manager.start();
                awaitSignalingReady(manager);
                manager.updatePresence(PRESENCE.publish(sessionAccount));
                NliConstants.LOG.info("Published NetherLink client presence for {}", sessionAccount.getMcProfileName());
                message(minecraft, new net.minecraft.network.chat.TranslatableComponent("netherlink.client.friends.opened"));
            } catch (RuntimeException e) {
                ((NetherLinkIntegratedServer)integratedServer).nli$setFriendsOpen(false);
                if (isMinecraftTokenRejected(e)) {
                    NliConstants.LOG.warn("Launcher Minecraft token was rejected; restart the game to get a fresh token", e);
                    message(minecraft, new net.minecraft.network.chat.TranslatableComponent("netherlink.client.friends.token_rejected"));
                } else {
                    NliConstants.LOG.warn("Failed to publish NetherLink client presence", e);
                    message(minecraft, new net.minecraft.network.chat.TranslatableComponent("netherlink.client.friends.failed"));
                }
                stopManager();
            } finally {
                PENDING.set(false);
            }
        });
    }

    public static void revoke(Minecraft minecraft) {
        EXECUTOR.execute(() -> {
            try {
                if (account != null) {
                    PRESENCE.revoke(account);
                }
            } catch (RuntimeException e) {
                NliConstants.LOG.warn("Failed to revoke NetherLink client presence", e);
            } finally {
                stopManager();
                message(minecraft, new net.minecraft.network.chat.TranslatableComponent("netherlink.client.friends.closed"));
            }
        });
    }

    public static void shutdown() {
        EXECUTOR.execute(() -> {
            try {
                if (account != null) {
                    PRESENCE.revoke(account);
                }
            } catch (RuntimeException e) {
                NliConstants.LOG.warn("Failed to revoke NetherLink client presence during shutdown", e);
            } finally {
                stopManager();
            }
        });
    }

    public static boolean isFriendsOpen(IntegratedServer integratedServer) {
        return ((NetherLinkIntegratedServer)integratedServer).nli$isFriendsOpen();
    }

    private static void awaitSignalingReady(ServerP2PManager p2pManager) {
        try {
            p2pManager.awaitSignalingReady(SIGNALING_READY_TIMEOUT).join();
        } catch (CompletionException e) {
            throw new NetherLinkAuthException("Signaling did not become ready before publishing client presence", e);
        }
    }

    private static void stopManager() {
        if (manager != null) {
            manager.shutdown();
            manager = null;
        }
        account = null;
    }

    private static void message(Minecraft minecraft, Component message) {
        minecraft.execute(() -> minecraft.gui.getChat().addMessage(message));
    }

    private static boolean isMinecraftTokenRejected(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof PresencePublisher.UnauthorizedException || current instanceof SignalingException.SignalingAuthException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
