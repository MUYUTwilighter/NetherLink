package cool.muyucloud.netherlink.link.bridge;

import io.netty.channel.Channel;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Hands an established guest P2P channel to a local Minecraft server. */
@FunctionalInterface
public interface LinkServerConnectionBridge {
    /**
     * Accepts a guest channel and optional authenticated profile expectation. Implementations must
     * switch to the server thread where required and assume ownership of the channel.
     */
    void accept(Channel channel, @Nullable UUID profileId);
}
