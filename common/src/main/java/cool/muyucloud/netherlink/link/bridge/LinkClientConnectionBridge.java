package cool.muyucloud.netherlink.link.bridge;

import io.netty.channel.Channel;

/** Hands an established P2P channel to the local Minecraft client connection flow. */
@FunctionalInterface
public interface LinkClientConnectionBridge {
    /**
     * Begins joining through {@code channel}. Implementations must arrange the required client-thread
     * hand-off and assume ownership of the channel on successful return.
     */
    void join(Channel channel);
}
