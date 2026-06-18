package cool.muyucloud.netherlink.client;

import cool.muyucloud.netherlink.link.bridge.LinkClientConnectionBridge;
import cool.muyucloud.netherlink.mixin.MinecraftAccessor;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.timeout.ReadTimeoutHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.login.LoginProtocols;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.network.EventLoopGroupHolder;

public final class MinecraftClientConnectionBridge implements LinkClientConnectionBridge {
    private final Minecraft minecraft;

    public MinecraftClientConnectionBridge(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public void join(Channel channel) {
        this.minecraft.execute(() -> {
            this.minecraft.disconnect(new ProgressScreen(true), false);
            Connection connection = this.connection(channel);
            LevelLoadTracker tracker = new LevelLoadTracker(0L);
            connection.initiateServerboundPlayConnection(
                "rtc-peer",
                0,
                LoginProtocols.SERVERBOUND,
                LoginProtocols.CLIENTBOUND,
                new ClientHandshakePacketListenerImpl(
                    connection,
                    this.minecraft,
                    new ServerData("NetherLink", "rtc-peer", ServerData.Type.OTHER),
                    null,
                    false,
                    null,
                    _ -> {
                    },
                    tracker,
                    null
                ),
                false
            );
            connection.send(new ServerboundHelloPacket(this.minecraft.getUser().getName(), this.minecraft.getUser().getProfileId()));
            ((MinecraftAccessor)this.minecraft).nli$setPendingConnection(connection);
        });
    }

    private Connection connection(Channel channel) {
        Connection connection = new Connection(PacketFlow.CLIENTBOUND);
        channel.pipeline().addLast(new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel ch) {
                ChannelPipeline pipeline = ch.pipeline().addLast("timeout", new ReadTimeoutHandler(30));
                Connection.configureSerialization(pipeline, PacketFlow.CLIENTBOUND, false, null);
                connection.configurePacketHandler(pipeline);
            }
        });
        EventLoopGroupHolder.local().eventLoopGroup().register(channel).syncUninterruptibly();
        return connection;
    }
}
