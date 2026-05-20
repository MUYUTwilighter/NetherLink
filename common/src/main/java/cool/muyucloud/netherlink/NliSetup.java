package cool.muyucloud.netherlink;

import cool.muyucloud.netherlink.account.AccountManager;
import net.minecraft.server.MinecraftServer;

public class NliSetup {
    public static void init() {
    }

    public static void onServerStarted(MinecraftServer server) {
        AccountManager.start(server);
    }

    public static void onServerStopping(MinecraftServer server) {
        AccountManager.stop(server);
    }
}
