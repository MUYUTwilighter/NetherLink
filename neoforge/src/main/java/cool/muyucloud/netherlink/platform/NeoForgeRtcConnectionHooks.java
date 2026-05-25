package cool.muyucloud.netherlink.platform;

import cool.muyucloud.netherlink.NliConstants;
import io.netty.channel.Channel;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.ChannelAttributes;
import net.neoforged.neoforge.network.registration.NetworkPayloadSetup;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings({"unused", "UnstableApiUsage"})
public final class NeoForgeRtcConnectionHooks {
    private NeoForgeRtcConnectionHooks() {
    }

    public static void prepare(Channel channel, String side) {
        @Nullable ConnectionType type = channel.attr(ChannelAttributes.CONNECTION_TYPE).get();
        if (type != null) {
            NliConstants.LOG.info("[P2P-Netty][neoforge] RTC {} connection already has type={}", side, type);
            return;
        }
        channel.attr(ChannelAttributes.CONNECTION_TYPE).set(ConnectionType.OTHER);
        if (channel.attr(ChannelAttributes.PAYLOAD_SETUP).get() == null) {
            channel.attr(ChannelAttributes.PAYLOAD_SETUP).set(NetworkPayloadSetup.empty());
        }
        NliConstants.LOG.info("[P2P-Netty][neoforge] Prepared RTC {} connection with default type={}", side, ConnectionType.OTHER);
    }
}
