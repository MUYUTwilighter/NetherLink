package cool.muyucloud.netherlink.p2p;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public final class RtcConnectionPlatformHandler extends ChannelInboundHandlerAdapter {
    private final String side;
    private boolean prepared;

    public RtcConnectionPlatformHandler(String side) {
        this.side = side;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        if (!this.prepared) {
            this.prepared = true;
            RtcConnectionPlatformHooks.prepare(ctx.channel(), this.side);
        }
        super.channelActive(ctx);
    }
}
