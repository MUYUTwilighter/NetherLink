package cool.muyucloud.netherlink.p2p;

import io.netty.channel.ChannelPipeline;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.Varint21FrameDecoder;
import net.minecraft.network.Varint21LengthFieldPrepender;
import net.minecraft.network.protocol.PacketFlow;

public final class NetworkPipelineCompatibility {
    private NetworkPipelineCompatibility() {
    }

    public static void configureSerialization(ChannelPipeline pipeline, PacketFlow receiving) {
        PacketFlow sending = receiving == PacketFlow.CLIENTBOUND ? PacketFlow.SERVERBOUND : PacketFlow.CLIENTBOUND;
        pipeline.addLast("splitter", new Varint21FrameDecoder());
        pipeline.addLast("decoder", new PacketDecoder(receiving));
        pipeline.addLast("prepender", new Varint21LengthFieldPrepender());
        pipeline.addLast("encoder", new PacketEncoder(sending));
    }
}
