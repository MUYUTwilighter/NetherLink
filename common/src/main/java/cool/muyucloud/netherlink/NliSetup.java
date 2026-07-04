package cool.muyucloud.netherlink;

import cool.muyucloud.netherlink.account.AccountManager;
import net.minecraft.server.MinecraftServer;

public class NliSetup {
    public static void init() {
    }

    public static void onServerStarting(MinecraftServer server) {
        NliConstants.server = server;
    }

    public static void onServerStarted(MinecraftServer server) {
        NliConstants.server = server;
        AccountManager.start(server);
    }

    public static void onServerStopping(MinecraftServer server) {
        AccountManager.stop(server);
        if (NliConstants.server == server) {
            NliConstants.server = null;
        }
    }

    public static void onServerStopped(MinecraftServer server) {
        NliConstants.server = null;
    }
}
