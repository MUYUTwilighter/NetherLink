package cool.muyucloud.netherlink.client;

import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.access.MinecraftAccess;
import cool.muyucloud.netherlink.bridge.ConnectionBridgeEvents;
import cool.muyucloud.netherlink.link.bridge.LinkClientConnectionBridge;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.timeout.ReadTimeoutHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;

import java.util.Optional;
import java.util.function.Consumer;

public final class MinecraftClientConnectionBridge implements LinkClientConnectionBridge {
    private final Minecraft minecraft;
    private final Screen progressScreen;
    private final Consumer<Component> statusChanged;

    public MinecraftClientConnectionBridge(Minecraft minecraft) {
        this(minecraft, new ProgressScreen(true), ignored1 -> {
        });
    }

    public MinecraftClientConnectionBridge(Minecraft minecraft, Screen progressScreen, Consumer<Component> statusChanged) {
        this.minecraft = minecraft;
        this.progressScreen = progressScreen;
        this.statusChanged = statusChanged;
    }

    @Override
    public void join(Channel channel) {
        this.minecraft.execute(() -> {
            try {
                this.statusChanged.accept(Component.translatable("netherlink.join.minecraft.connecting"));
                this.minecraft.clearLevel(this.progressScreen);
                Connection connection = this.connection(channel);
                connection.setListener(
                    new ClientHandshakePacketListenerImpl(
                        connection,
                        this.minecraft,
                        new ServerData("NetherLink", "rtc-peer", false),
                        null,
                        false,
                        null,
                        this.statusChanged
                    )
                );
                connection.send(new ClientIntentionPacket("rtc-peer", 0, ConnectionProtocol.LOGIN));
                connection.send(new ServerboundHelloPacket(this.minecraft.getUser().getName(), Optional.ofNullable(this.minecraft.getUser().getProfileId())));
                MinecraftAccess.setPendingConnection(connection);
            } catch (RuntimeException error) {
                NliConstants.LOG.error("[P2P-Netty][client] Failed to hand RTC channel to Minecraft", error);
                throw error;
            }
        });
    }

    private Connection connection(Channel channel) {
        Connection connection = new Connection(PacketFlow.CLIENTBOUND);
        channel.pipeline().addLast(new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel ch) {
                ChannelPipeline pipeline = ch.pipeline().addLast("timeout", new ReadTimeoutHandler(30));
                Connection.configureSerialization(pipeline, PacketFlow.CLIENTBOUND);
                ConnectionBridgeEvents.setupClientPipeline(new ConnectionBridgeEvents.ClientPipelineContext(connection, ch, pipeline));
                pipeline.addLast("packet_handler", connection);
            }
        });
        Connection.LOCAL_WORKER_GROUP.get().register(channel).syncUninterruptibly();
        return connection;
    }
}
