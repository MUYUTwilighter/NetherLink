package cool.muyucloud.netherlink;

import cool.muyucloud.netherlink.account.AccountManager;
import cool.muyucloud.netherlink.teacon.block.IntroCardSignLogic;
import net.minecraft.server.MinecraftServer;

public class NliSetup {
    public static void init() {
    }

    public static void onServerStarted(MinecraftServer server) {
        AccountManager.start(server);
        IntroCardSignLogic.loadPlayerUsage();
    }

    public static void onServerStopping(MinecraftServer server) {
        AccountManager.stop(server);
        IntroCardSignLogic.savePlayerUsage();
    }
}
