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
import net.minecraft.util.HttpUtil;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
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
        int port = integratedServer.getPort() > 0 ? integratedServer.getPort() : HttpUtil.getAvailablePort();
        setFriendsOpen(minecraft, integratedServer, open, port);
    }

    public static void setFriendsOpen(Minecraft minecraft, IntegratedServer integratedServer, boolean open, int port) {
        if (open) {
            ClientTermsController.runAfterAcceptance(
                minecraft,
                minecraft.gui.screen(),
                () -> setFriendsOpenAccepted(minecraft, integratedServer, port)
            );
        } else {
            NetherLinkIntegratedServer bridge = (NetherLinkIntegratedServer)integratedServer;
            if (bridge.nli$isFriendsOpen()) {
                integratedServer.unpublishServer();
            } else {
                bridge.nli$setFriendsOpen(false);
                revoke(minecraft);
            }
        }
    }

    private static void setFriendsOpenAccepted(Minecraft minecraft, IntegratedServer integratedServer, int port) {
        NetherLinkIntegratedServer bridge = (NetherLinkIntegratedServer)integratedServer;
        if (!bridge.nli$publishFriendsNetwork(port)) {
            bridge.nli$setFriendsOpen(false);
            message(minecraft, Component.translatable("netherlink.client.friends.failed"));
            return;
        }
        publish(minecraft, integratedServer);
    }

    public static void publish(Minecraft minecraft, IntegratedServer integratedServer) {
        publish(minecraft, integratedServer, true);
    }

    public static CompletableFuture<Boolean> refreshPresence(Minecraft minecraft) {
        IntegratedServer integratedServer = minecraft.getSingleplayerServer();
        if (integratedServer == null || !integratedServer.isPublished() || !isFriendsOpen(integratedServer)) {
            return CompletableFuture.completedFuture(false);
        }
        return publish(minecraft, integratedServer, false).thenApply(ignored -> true);
    }

    private static CompletableFuture<Void> publish(Minecraft minecraft, IntegratedServer integratedServer, boolean notify) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        if (!integratedServer.isPublished()) {
            if (notify) {
                message(minecraft, Component.translatable("netherlink.client.friends.failed"));
            }
            result.complete(null);
            return result;
        }
        if (!PENDING.compareAndSet(false, true)) {
            result.complete(null);
            return result;
        }
        EXECUTOR.execute(() -> {
            try {
                LauncherSessionAccount sessionAccount = new LauncherSessionAccount(minecraft.getUser());
                if (!sessionAccount.isUsable()) {
                    NetherLinkIntegratedServer bridge = (NetherLinkIntegratedServer)integratedServer;
                    boolean wasFriendsOpen = bridge.nli$isFriendsOpen();
                    bridge.nli$setFriendsOpen(false);
                    NliConstants.LOG.warn("Launcher account is missing a Minecraft access token or profile id; NetherLink friends access is unavailable");
                    if (notify) {
                        message(minecraft, Component.translatable("netherlink.client.friends.unavailable"));
                    }
                    if (wasFriendsOpen) {
                        minecraft.execute(integratedServer::unpublishServer);
                    }
                    result.complete(null);
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
                if (notify) {
                    message(minecraft, Component.translatable("netherlink.client.friends.opened"));
                }
                result.complete(null);
            } catch (RuntimeException e) {
                NetherLinkIntegratedServer bridge = (NetherLinkIntegratedServer)integratedServer;
                boolean wasFriendsOpen = bridge.nli$isFriendsOpen();
                bridge.nli$setFriendsOpen(false);
                if (isMinecraftTokenRejected(e)) {
                    NliConstants.LOG.warn("Launcher Minecraft token was rejected; restart the game to get a fresh token", e);
                    if (notify) {
                        message(minecraft, Component.translatable("netherlink.client.friends.token_rejected"));
                    }
                } else {
                    NliConstants.LOG.warn("Failed to publish NetherLink client presence", e);
                    if (notify) {
                        message(minecraft, Component.translatable("netherlink.client.friends.failed"));
                    }
                }
                stopPublication();
                if (wasFriendsOpen) {
                    minecraft.execute(integratedServer::unpublishServer);
                }
                result.completeExceptionally(e);
            } finally {
                PENDING.set(false);
            }
        });
        return result;
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
        minecraft.execute(() -> minecraft.gui.hud.getChat().addClientSystemMessage(message));
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

