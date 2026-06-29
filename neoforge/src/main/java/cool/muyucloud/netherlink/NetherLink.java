package cool.muyucloud.netherlink;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@Mod(NliConstants.MOD_ID)
public class NetherLink {
    public NetherLink() {
        NliSetup.init();
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private void onServerStarted(ServerStartedEvent event) {
        NliSetup.onServerStarted(event.getServer());
    }

    private void onServerStopping(ServerStoppingEvent event) {
        NliSetup.onServerStopping(event.getServer());
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        NliConstants.SERVER_COMMAND.register(event.getDispatcher());
    }
}
