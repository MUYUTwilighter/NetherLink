package cool.muyucloud.netherlink.platform;

import cool.muyucloud.netherlink.NliConstants;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import net.minecraftforge.fml.util.thread.SidedThreadGroups;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("unused")
public final class ForgeRtcEventLoops {
    private static final EventLoopGroup SERVER_GROUP = new DefaultEventLoopGroup(2, serverThreadFactory());

    private ForgeRtcEventLoops() {
    }

    public static EventLoopGroup serverGroup() {
        return SERVER_GROUP;
    }

    private static ThreadFactory serverThreadFactory() {
        AtomicInteger id = new AtomicInteger();
        return runnable -> {
            Thread thread = SidedThreadGroups.SERVER.newThread(runnable);
            thread.setName("NetherLink RTC Server IO #" + id.incrementAndGet());
            thread.setDaemon(true);
            NliConstants.LOG.debug("[P2P-Netty][forge] Created server-side RTC thread {}", thread.getName());
            return thread;
        };
    }
}
