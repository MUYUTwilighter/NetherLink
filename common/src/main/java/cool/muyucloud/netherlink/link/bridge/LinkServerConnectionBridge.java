package cool.muyucloud.netherlink.link.bridge;

import io.netty.channel.Channel;

/** Hands an established guest P2P channel to a local Minecraft server. */
@FunctionalInterface
public interface LinkServerConnectionBridge {
    /**
     * Accepts a guest channel. Implementations must switch to the server thread where required and
     * assume ownership of the channel.
     */
    void accept(Channel channel);
}
