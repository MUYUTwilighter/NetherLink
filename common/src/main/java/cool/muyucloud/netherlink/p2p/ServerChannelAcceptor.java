package cool.muyucloud.netherlink.p2p;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.timeout.ReadTimeoutHandler;
import net.minecraft.network.Connection;
import net.minecraft.network.RateKickingConnection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerConnectionListener;
import net.minecraft.server.network.ServerHandshakePacketListenerImpl;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.UUID;

public final class ServerChannelAcceptor {
    private static final @Nullable Method SET_INTENDED_PROFILE_ID = findSetIntendedProfileId();

    private ServerChannelAcceptor() {
    }

    public static void accept(MinecraftServer server, Channel channel, @Nullable UUID profileId) {
        ServerConnectionListener listener = server.getConnection();
        channel.pipeline().addLast(new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel ch) {
                int rateLimitPacketsPerSecond = server.getRateLimitPacketsPerSecond();
                Connection connection = rateLimitPacketsPerSecond > 0
                    ? new RateKickingConnection(rateLimitPacketsPerSecond)
                    : new Connection(PacketFlow.SERVERBOUND);
                ChannelPipeline pipeline = ch.pipeline().addLast("timeout", (ChannelHandler)new ReadTimeoutHandler(30));
                Connection.configureSerialization(pipeline, PacketFlow.SERVERBOUND, false, null);
                connection.configurePacketHandler(pipeline);
                connection.setListenerForServerboundHandshake(new ServerHandshakePacketListenerImpl(server, connection));
                setIntendedProfileId(connection, profileId);
                listener.getConnections().add(connection);
            }
        });
        Connection.LOCAL_WORKER_GROUP.get().register(channel).syncUninterruptibly();
    }

    private static void setIntendedProfileId(Connection connection, @Nullable UUID profileId) {
        if (SET_INTENDED_PROFILE_ID == null) {
            return;
        }
        try {
            SET_INTENDED_PROFILE_ID.invoke(connection, profileId);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to set intended profile id", e);
        }
    }

    private static @Nullable Method findSetIntendedProfileId() {
        try {
            return Connection.class.getMethod("setIntendedProfileId", UUID.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
