package cool.muyucloud.netherlink.bridge;

import cool.muyucloud.netherlink.link.bridge.LinkServerConnectionBridge;
import io.netty.channel.Channel;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public final class MinecraftServerConnectionBridge implements LinkServerConnectionBridge {
    private final MinecraftServer server;

    public MinecraftServerConnectionBridge(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public void accept(Channel channel, @Nullable UUID profileId) {
        this.server.execute(() -> this.server.getConnection().acceptChannel(channel, profileId));
    }
}
