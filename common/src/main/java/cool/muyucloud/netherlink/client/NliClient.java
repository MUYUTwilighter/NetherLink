package cool.muyucloud.netherlink.client;

import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.access.MinecraftAccess;

import java.util.Optional;

public final class NliClient {
    public static void init() {
        NliConstants.windowTitle = () -> Optional.ofNullable(MinecraftAccess.createWindowTitle());
    }
}
