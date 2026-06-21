package cool.muyucloud.netherlink.teacon.client;

import cool.muyucloud.netherlink.client.ClientTermsController;
import cool.muyucloud.netherlink.teacon.TeaconClientHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public final class TeaconClientActions implements TeaconClientHooks.Actions {
    private static final TeaconClientActions INSTANCE = new TeaconClientActions();

    private TeaconClientActions() {
    }

    public static void install() {
        TeaconClientHooks.install(INSTANCE);
    }

    @Override
    public void openFriendCard(@Nullable String targetName, UUID targetId) {
        Minecraft minecraft = Minecraft.getInstance();
        String resolvedName = targetName;
        if ((resolvedName == null || resolvedName.isBlank()) && minecraft.getConnection() != null) {
            var playerInfo = minecraft.getConnection().getPlayerInfo(targetId);
            if (playerInfo != null) {
                resolvedName = playerInfo.getProfile().name();
            }
        }
        String finalName = resolvedName;
        ClientTermsController.runAfterAcceptance(
            minecraft,
            minecraft.screen,
            () -> minecraft.setScreen(new FriendCardFriendScreen(finalName, targetId))
        );
    }

    @Override
    public void openOwnerMenu(BlockPos pos) {
        Minecraft.getInstance().setScreen(new IntroCardOwnerScreen(pos));
    }
}
