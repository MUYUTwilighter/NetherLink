package cool.muyucloud.netherlink.p2p;

import cool.muyucloud.netherlink.NliConstants;
import io.netty.channel.EventLoopGroup;
import net.minecraft.network.Connection;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class LoaderNetworkEventLoops {
    private static final @Nullable Method FORGE_SERVER_GROUP = findForgeServerGroup();

    private LoaderNetworkEventLoops() {
    }

    static EventLoopGroup serverGroup() {
        if (FORGE_SERVER_GROUP == null) {
            return Connection.LOCAL_WORKER_GROUP.get();
        }
        try {
            return (EventLoopGroup)FORGE_SERVER_GROUP.invoke(null);
        } catch (IllegalAccessException | InvocationTargetException e) {
            NliConstants.LOG.warn("[P2P-Netty] Failed to obtain loader server EventLoopGroup, using local worker", e);
            return Connection.LOCAL_WORKER_GROUP.get();
        }
    }

    private static @Nullable Method findForgeServerGroup() {
        try {
            Class<?> eventLoops = Class.forName("cool.muyucloud.netherlink.platform.ForgeRtcEventLoops");
            return eventLoops.getMethod("serverGroup");
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            return null;
        }
    }
}
