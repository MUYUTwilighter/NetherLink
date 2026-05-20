package cool.muyucloud.netherlink;


import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(NliConstants.MOD_ID)
public class NetherLink {
    public NetherLink(IEventBus eventBus) {
        NliSetup.init();
    }
}