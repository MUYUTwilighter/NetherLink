package cool.muyucloud.netherlink.mixin;

import cool.muyucloud.netherlink.client.ClientConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.FriendToast;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.multiplayer.p2p.FriendJoinHandler;
import net.minecraft.client.multiplayer.p2p.P2PManager;
import net.minecraft.client.multiplayer.p2p.SignalingMessage;
import net.minecraft.client.multiplayer.p2p.client.SignalingServiceClient;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(FriendJoinHandler.class)
public abstract class FriendJoinHandlerMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private P2PManager manager;

    @Shadow
    @Final
    private SignalingServiceClient signaling;

    @Shadow
    @Final
    private static Logger LOGGER;

    @Shadow
    protected abstract CompletableFuture<@Nullable Void> sendJoinAccepted(UUID peerPmid, String sessionId);

    @Shadow
    protected abstract void clearHostInvite(UUID peerPmid);

    @Shadow
    @Final
    private ConcurrentHashMap<UUID, String> incomingJoinRequests;

    @Shadow
    abstract void notifyJoinStateChanged();

    @Inject(method = "handleJoinRequest", at = @At("HEAD"), cancellable = true)
    public void onHandleJoinRequest(UUID fromPmid, String sessionId, CallbackInfo ci) {
        ci.cancel();
        IntegratedServer server = this.minecraft.getSingleplayerServer();
        if (server == null) return;
        if (!this.manager.isHostingP2P()) {
            this.signaling.sendClientMessage(fromPmid, SignalingMessage.joinRejected(sessionId)).exceptionally((err) -> {
                LOGGER.warn("[P2P][host] Failed to reject join request for session {}: {}", sessionId, err.getMessage());
                return null;
            });
        } else if (!this.minecraft.getPlayerSocialManager().isFriendsPmid(fromPmid)) {
            LOGGER.debug("[P2P][host] Ignoring join request (not a friend)");
        } else if (this.minecraft.getPlayerSocialManager().getPresenceHandler().isInvitedPmid(fromPmid)) {
            this.sendJoinAccepted(fromPmid, sessionId).thenRun(() -> this.clearHostInvite(fromPmid)).exceptionally((_) -> null);
        } else if (server.getMultiplayerScope() == ClientConstants.INTEGRATED_SERVER) {
            this.incomingJoinRequests.put(fromPmid, sessionId);
            this.notifyJoinStateChanged();
            this.sendJoinAccepted(fromPmid, sessionId).thenRun(() -> this.clearHostInvite(fromPmid)).exceptionally((_) -> null);
        } else {
            this.incomingJoinRequests.put(fromPmid, sessionId);
            this.notifyJoinStateChanged();
            UUID peerProfileId = this.minecraft.getPlayerSocialManager().getPresenceHandler().getProfileIdFromPmid(fromPmid);
            Optional<PlayerSocialManager.PlayerData> friendData = this.minecraft.getPlayerSocialManager().getFriends().stream().filter((playerData) -> playerData.id().equals(peerProfileId)).findAny();
            friendData.ifPresent((friend) -> this.minecraft.execute(() -> {
                PlayerSkin friendSkin = this.minecraft.playerSkinRenderCache().getOrDefault(ResolvableProfile.createUnresolved(friend.id())).playerSkin();
                FriendToast.showFriendJoinRequest(this.minecraft, friend.name(), friendSkin);
            }));
        }
    }
}
