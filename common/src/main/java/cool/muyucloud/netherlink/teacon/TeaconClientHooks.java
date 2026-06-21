package cool.muyucloud.netherlink.teacon;

import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/** Client actions exposed to common Teacon interaction logic without loading client classes. */
public final class TeaconClientHooks {
    private static Actions actions = Actions.NONE;

    private TeaconClientHooks() {
    }

    public static void install(Actions clientActions) {
        actions = Objects.requireNonNull(clientActions);
    }

    public static void openFriendCard(@Nullable String targetName, UUID targetId) {
        actions.openFriendCard(targetName, targetId);
    }

    public static void openOwnerMenu(BlockPos pos) {
        actions.openOwnerMenu(pos);
    }

    public interface Actions {
        Actions NONE = new Actions() {
            @Override
            public void openFriendCard(@Nullable String targetName, UUID targetId) {
            }

            @Override
            public void openOwnerMenu(BlockPos pos) {
            }
        };

        void openFriendCard(@Nullable String targetName, UUID targetId);

        void openOwnerMenu(BlockPos pos);
    }
}
