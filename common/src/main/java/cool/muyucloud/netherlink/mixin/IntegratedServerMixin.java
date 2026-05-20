package cool.muyucloud.netherlink.mixin;

import com.mojang.datafixers.DataFixer;
import cool.muyucloud.netherlink.client.ClientConstants;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.server.notifications.NotificationManager;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.Proxy;
import java.util.Optional;

@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin extends MinecraftServer {
    @Shadow
    public abstract MinecraftServer.MultiplayerScope getMultiplayerScope();

    public IntegratedServerMixin(Thread serverThread, LevelStorageSource.LevelStorageAccess storageSource, PackRepository packRepository, WorldStem worldStem, Optional<GameRules> gameRules, Proxy proxy, DataFixer fixerUpper, Services services, LevelLoadListener levelLoadListener, boolean propagatesCrashes, NotificationManager notificationManager) {
        super(serverThread, storageSource, packRepository, worldStem, gameRules, proxy, fixerUpper, services, levelLoadListener, propagatesCrashes, notificationManager);
    }

    @Inject(method = "isPublishedOnline", at = @At("RETURN"), cancellable = true)
    public void modifyIsPublishedOnlineReturnValue(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(
            cir.getReturnValue() || this.isRunning() && this.isPublished() && this.getMultiplayerScope() == ClientConstants.INTEGRATED_SERVER
        );
    }
}
