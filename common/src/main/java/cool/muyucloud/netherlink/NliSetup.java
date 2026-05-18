package cool.muyucloud.netherlink;

import cool.muyucloud.netherlink.account.AccountManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;

public class NliSetup {
    public static void init() {
    }

    public static void onServerStarting(MinecraftServer server) {
        if (server instanceof DedicatedServer dedicatedServer) {
            NliConstants.SERVER = dedicatedServer;
        }
    }

    public static void onServerStarted(MinecraftServer server) {
        if (server instanceof DedicatedServer) {
            AccountManager.load();
            AccountManager.publish();
        }
    }

    public static void onServerStopping(MinecraftServer server) {
        if (server instanceof DedicatedServer) {
            AccountManager.dump();
            AccountManager.revoke();
            AccountManager.clearRequests();
        }
    }
}