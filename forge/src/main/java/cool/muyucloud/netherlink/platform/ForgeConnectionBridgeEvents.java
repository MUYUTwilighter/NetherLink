package cool.muyucloud.netherlink.platform;

import cool.muyucloud.netherlink.bridge.ConnectionBridgeEvents;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraftforge.network.NetworkHooks;

public final class ForgeConnectionBridgeEvents {
    private ForgeConnectionBridgeEvents() {
    }

    public static void register() {
        ConnectionBridgeEvents.registerClientPipelineSetup(context -> context.pipeline().addLast(
            "netherlink_forge_login",
            new ChannelInboundHandlerAdapter() {
                @Override
                public void channelActive(ChannelHandlerContext handlerContext) throws Exception {
                    handlerContext.fireChannelActive();
                    NetworkHooks.registerClientLoginChannel(context.connection());
                }
            }
        ));
    }
}
