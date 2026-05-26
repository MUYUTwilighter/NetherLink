package cool.muyucloud.netherlink.p2p;

import java.util.function.Supplier;

final class RtcThreadContext {
    private static final ClassLoader LOADER = RtcThreadContext.class.getClassLoader();

    private RtcThreadContext() {
    }

    static void run(Runnable action) {
        ClassLoader previous = enter();
        try {
            action.run();
        } finally {
            exit(previous);
        }
    }

    static <T> T call(Supplier<T> action) {
        ClassLoader previous = enter();
        try {
            return action.get();
        } finally {
            exit(previous);
        }
    }

    static ClassLoader enter() {
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        if (previous != LOADER) {
            Thread.currentThread().setContextClassLoader(LOADER);
        }
        return previous;
    }

    static void exit(ClassLoader previous) {
        if (previous == null) {
            return;
        }
        if (Thread.currentThread().getContextClassLoader() != previous) {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    static void warmup() {
        // Force this helper to load on a Minecraft-managed thread before WebRTC native callbacks begin.
    }
}
