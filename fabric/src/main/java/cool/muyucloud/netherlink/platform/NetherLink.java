package cool.muyucloud.netherlink.platform;

import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.NliSetup;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class NetherLink implements ModInitializer {
    @Override
    public void onInitialize() {
        NliSetup.init();
        ServerLifecycleEvents.SERVER_STARTING.register(NliSetup::onServerStarting);
        ServerLifecycleEvents.SERVER_STARTED.register(NliSetup::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(NliSetup::onServerStopping);
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> {
            NliConstants.SERVER_COMMAND.register(dispatcher);
        });
    }
}
