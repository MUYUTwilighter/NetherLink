package cool.muyucloud.netherlink.bridge;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import net.minecraft.network.Connection;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ConnectionBridgeEvents {
    private static final List<ClientPipelineSetup> CLIENT_PIPELINE_SETUPS = new CopyOnWriteArrayList<>();

    private ConnectionBridgeEvents() {
    }

    public static void registerClientPipelineSetup(ClientPipelineSetup setup) {
        CLIENT_PIPELINE_SETUPS.add(setup);
    }

    public static void setupClientPipeline(ClientPipelineContext context) {
        for (ClientPipelineSetup setup : CLIENT_PIPELINE_SETUPS) {
            setup.setup(context);
        }
    }

    @FunctionalInterface
    public interface ClientPipelineSetup {
        void setup(ClientPipelineContext context);
    }

    public record ClientPipelineContext(Connection connection, Channel channel, ChannelPipeline pipeline) {
    }
}
