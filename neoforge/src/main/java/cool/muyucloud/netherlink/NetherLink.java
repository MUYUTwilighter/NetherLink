package cool.muyucloud.netherlink;


import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@Mod(NliConstants.MOD_ID)
public class NetherLink {
    static {
        NliConstants.platform = "NeoForge";
    }

    public NetherLink() {
        NliSetup.init();
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        NliSetup.onServerStarting(event.getServer());
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        NliSetup.onServerStarted(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        NliSetup.onServerStopping(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        NliSetup.onServerStopped(event.getServer());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        NliConstants.SERVER_COMMAND.register(event.getDispatcher());
    }
}
