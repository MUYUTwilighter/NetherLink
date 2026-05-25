package cool.muyucloud.netherlink.p2p;

import cool.muyucloud.netherlink.NliConstants;
import io.netty.channel.Channel;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class RtcConnectionPlatformHooks {
    private static final String NEOFORGE_HOOKS = "cool.muyucloud.netherlink.platform.NeoForgeRtcConnectionHooks";
    private static final Method PREPARE = findPrepareMethod();

    private RtcConnectionPlatformHooks() {
    }

    static void prepare(Channel channel, String side) {
        if (PREPARE == null) {
            return;
        }
        try {
            PREPARE.invoke(null, channel, side);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to access RTC connection platform hook", e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("RTC connection platform hook failed", cause);
        }
    }

    private static Method findPrepareMethod() {
        try {
            Class<?> hooks = Class.forName(NEOFORGE_HOOKS);
            return hooks.getMethod("prepare", Channel.class, String.class);
        } catch (ClassNotFoundException e) {
            return null;
        } catch (NoSuchMethodException e) {
            NliConstants.LOG.warn("[P2P-Netty] RTC connection platform hook exists but has no prepare(Channel, String) method", e);
            return null;
        }
    }
}
