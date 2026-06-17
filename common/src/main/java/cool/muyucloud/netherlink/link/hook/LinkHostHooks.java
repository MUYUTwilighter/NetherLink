package cool.muyucloud.netherlink.link.hook;

import cool.muyucloud.netherlink.account.MinecraftAccount;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class LinkHostHooks {
    private static final Map<String, Host> HOSTS = new ConcurrentHashMap<>();

    private LinkHostHooks() {
    }

    public static void setHost(String key, MinecraftAccount account, MinecraftServer server) {
        HOSTS.put(key, new Host(key, account, server));
    }

    @SuppressWarnings("unused")
    public static @Nullable Host host(String key) {
        return HOSTS.get(key);
    }

    public static Host requireHost(String key) {
        Host host = HOSTS.get(key);
        if (host == null) {
            throw new IllegalStateException("NetherLink host hook is not registered: " + key);
        }
        return host;
    }

    @SuppressWarnings("unused")
    public static void removeHost(String key) {
        HOSTS.remove(key);
    }

    public record Host(String key, MinecraftAccount account, MinecraftServer server) {
    }
}
