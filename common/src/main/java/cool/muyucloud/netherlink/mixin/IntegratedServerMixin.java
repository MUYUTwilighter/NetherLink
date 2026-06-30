package cool.muyucloud.netherlink.mixin;

import com.mojang.datafixers.DataFixer;
import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.client.ClientP2PController;
import cool.muyucloud.netherlink.client.NetherLinkIntegratedServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.server.notifications.NotificationManager;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.net.Proxy;
import java.util.Objects;
import java.util.Optional;

@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin extends MinecraftServer implements NetherLinkIntegratedServer {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    private int publishedPort;

    @Shadow
    private MinecraftServer.MultiplayerScope multiplayerScope;

    @Unique
    private boolean netherlink$friendsOpen;

    public IntegratedServerMixin(Thread serverThread, LevelStorageSource.LevelStorageAccess storageSource, PackRepository packRepository, WorldStem worldStem, Optional<GameRules> gameRules, Proxy proxy, DataFixer fixerUpper, Services services, LevelLoadListener levelLoadListener, boolean propagatesCrashes, NotificationManager notificationManager) {
        super(serverThread, storageSource, packRepository, worldStem, gameRules, proxy, fixerUpper, services, levelLoadListener, propagatesCrashes, notificationManager);
    }

    @Shadow
    public abstract boolean isPublished();

    @Shadow
    private void updateCommandsAllowedForOtherPlayers() {
        throw new AssertionError();
    }

    @Override
    public boolean nli$isFriendsOpen() {
        return this.netherlink$friendsOpen;
    }

    @Override
    public void nli$setFriendsOpen(boolean friendsOpen) {
        this.netherlink$friendsOpen = friendsOpen;
    }

    @Override
    public boolean nli$publishFriendsNetwork(int port) {
        if (this.netherlink$friendsOpen) {
            return true;
        }
        if (this.isPublished()) {
            return false;
        }

        try {
            this.minecraft.prepareForMultiplayer();
            Objects.requireNonNull(this.minecraft.getConnection()).prepareKeyPair();
            this.getConnection().startTcpServerListener(null, port);
            this.publishedPort = port;
            this.multiplayerScope = MinecraftServer.MultiplayerScope.LAN;
            this.netherlink$friendsOpen = true;
            this.updateCommandsAllowedForOtherPlayers();
            NliConstants.LOG.info("Published NetherLink friends network on port {}", port);
            return true;
        } catch (IOException error) {
            NliConstants.LOG.warn("Failed to publish NetherLink friends network on port {}", port, error);
            return false;
        }
    }

    @Inject(method = "unpublishServer", at = @At("HEAD"))
    private void netherlink$revokeFriendsNetwork(CallbackInfoReturnable<Boolean> cir) {
        if (this.netherlink$friendsOpen) {
            this.netherlink$friendsOpen = false;
            ClientP2PController.revoke(this.minecraft);
        }
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void onStopServer(CallbackInfo ci) {
        this.netherlink$shutdownClientP2P();
    }

    @Inject(method = "halt", at = @At("HEAD"))
    private void onHalt(boolean wait, CallbackInfo ci) {
        this.netherlink$shutdownClientP2P();
    }

    @Unique
    private void netherlink$shutdownClientP2P() {
        this.netherlink$friendsOpen = false;
        ClientP2PController.shutdown();
    }
}
