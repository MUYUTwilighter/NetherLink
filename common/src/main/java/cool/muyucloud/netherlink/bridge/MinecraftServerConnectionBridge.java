package cool.muyucloud.netherlink.bridge;

import cool.muyucloud.netherlink.link.bridge.LinkServerConnectionBridge;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.timeout.ReadTimeoutHandler;
import net.minecraft.network.Connection;
import net.minecraft.network.RateKickingConnection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.server.network.ServerConnectionListener;
import net.minecraft.server.network.ServerHandshakePacketListenerImpl;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class MinecraftServerConnectionBridge implements LinkServerConnectionBridge {
    private final MinecraftServer server;

    public MinecraftServerConnectionBridge(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public void accept(Channel channel, @Nullable UUID ignoredProfileId) {
        this.server.execute(() -> this.acceptOnServerThread(channel));
    }

    private void acceptOnServerThread(Channel channel) {
        ServerConnectionListener listener = this.server.getConnection();
        channel.pipeline().addLast(new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel ch) {
                int rateLimitPacketsPerSecond = MinecraftServerConnectionBridge.this.server.getRateLimitPacketsPerSecond();
                Connection connection = rateLimitPacketsPerSecond > 0
                    ? new RateKickingConnection(rateLimitPacketsPerSecond)
                    : new Connection(PacketFlow.SERVERBOUND);
                ChannelPipeline pipeline = ch.pipeline().addLast("timeout", new ReadTimeoutHandler(30));
                Connection.configureSerialization(pipeline, PacketFlow.SERVERBOUND, false, null);
                connection.configurePacketHandler(pipeline);
                connection.setListenerForServerboundHandshake(new ServerHandshakePacketListenerImpl(MinecraftServerConnectionBridge.this.server, connection));
                listener.getConnections().add(connection);
            }
        });
        EventLoopGroupHolder.local().eventLoopGroup().register(channel).syncUninterruptibly();
    }
}