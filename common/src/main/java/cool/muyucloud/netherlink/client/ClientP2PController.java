package cool.muyucloud.netherlink.client;

import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.bridge.MinecraftServerConnectionBridge;
import cool.muyucloud.netherlink.link.LinkServices;
import cool.muyucloud.netherlink.link.exception.LinkUnauthorizedException;
import cool.muyucloud.netherlink.link.hook.LinkContextHooks;
import cool.muyucloud.netherlink.link.model.LinkHostPublication;
import cool.muyucloud.netherlink.link.model.LinkPresenceUpdate;
import cool.muyucloud.netherlink.link.service.LinkRuntimeService;
import cool.muyucloud.netherlink.link.transport.SignalingException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ClientP2PController {
    private static final Duration SIGNALING_READY_TIMEOUT = Duration.ofSeconds(15L);
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "NetherLink Client P2P");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean PENDING = new AtomicBoolean();
    private static LinkHostPublication publication;

    private ClientP2PController() {
    }

    public static void setFriendsOpen(Minecraft minecraft, IntegratedServer integratedServer, boolean open) {
        if (open) {
            ClientTermsController.runAfterAcceptance(
                minecraft,
                minecraft.screen,
                () -> setFriendsOpenAccepted(minecraft, integratedServer)
            );
        } else {
            ((NetherLinkIntegratedServer)integratedServer).nli$setFriendsOpen(false);
            revoke(minecraft);
        }
    }

    private static void setFriendsOpenAccepted(Minecraft minecraft, IntegratedServer integratedServer) {
        ((NetherLinkIntegratedServer)integratedServer).nli$setFriendsOpen(true);
        publish(minecraft, integratedServer);
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
                    NliConstants.LOG.warn("Launcher account is missing a Minecraft access token or profile id; NetherLink friends access is unavailable");
                    message(minecraft, Component.translatable("netherlink.client.friends.unavailable"));
                    return;
                }
                stopPublication();
                String hostKey = LinkRuntimeService.CLIENT_KEY;
                String instanceName = NliConstants.resolveInstanceName();
                LinkContextHooks.setClientConnection(hostKey, sessionAccount, instanceName, new MinecraftClientConnectionBridge(minecraft));
                LinkContextHooks.setServerConnection(hostKey, sessionAccount, instanceName, new MinecraftServerConnectionBridge(integratedServer));
                LinkServices.current().runtime().open(hostKey).join();
                publication = LinkServices.current().hosting().publish(
                    hostKey,
                    LinkPresenceUpdate.hosting(instanceName),
                    SIGNALING_READY_TIMEOUT
                );
                NliConstants.LOG.info("Published NetherLink client presence for {}", sessionAccount.getMcProfileName());
                message(minecraft, Component.translatable("netherlink.client.friends.opened"));
            } catch (RuntimeException e) {
                ((NetherLinkIntegratedServer)integratedServer).nli$setFriendsOpen(false);
                if (isMinecraftTokenRejected(e)) {
                    NliConstants.LOG.warn("Launcher Minecraft token was rejected; restart the game to get a fresh token", e);
                    message(minecraft, Component.translatable("netherlink.client.friends.token_rejected"));
                } else {
                    NliConstants.LOG.warn("Failed to publish NetherLink client presence", e);
                    message(minecraft, Component.translatable("netherlink.client.friends.failed"));
                }
                stopPublication();
            } finally {
                PENDING.set(false);
            }
        });
    }

    public static void revoke(Minecraft minecraft) {
        EXECUTOR.execute(() -> {
            try {
                if (publication != null) {
                    publication.close();
                }
            } catch (RuntimeException e) {
                NliConstants.LOG.warn("Failed to revoke NetherLink client presence", e);
            } finally {
                publication = null;
                LinkContextHooks.removeServerConnection(LinkRuntimeService.CLIENT_KEY);
                message(minecraft, Component.translatable("netherlink.client.friends.closed"));
            }
        });
    }

    public static void shutdown() {
        EXECUTOR.execute(() -> {
            try {
                if (publication != null) {
                    publication.close();
                }
            } catch (RuntimeException e) {
                NliConstants.LOG.warn("Failed to revoke NetherLink client presence during shutdown", e);
            } finally {
                publication = null;
                LinkContextHooks.removeServerConnection(LinkRuntimeService.CLIENT_KEY);
            }
        });
    }

    public static boolean isFriendsOpen(IntegratedServer integratedServer) {
        return ((NetherLinkIntegratedServer)integratedServer).nli$isFriendsOpen();
    }

    private static void stopPublication() {
        if (publication != null) {
            publication.close();
            publication = null;
        }
        LinkContextHooks.removeServerConnection(LinkRuntimeService.CLIENT_KEY);
    }

    private static void message(Minecraft minecraft, Component message) {
        minecraft.execute(() -> minecraft.gui.getChat().addMessage(message));
    }

    private static boolean isMinecraftTokenRejected(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof LinkUnauthorizedException || current instanceof SignalingException.SignalingAuthException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
