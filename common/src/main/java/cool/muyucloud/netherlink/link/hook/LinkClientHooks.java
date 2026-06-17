package cool.muyucloud.netherlink.link.hook;

import cool.muyucloud.netherlink.account.MinecraftAccount;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

public final class LinkClientHooks {
    private static volatile @Nullable Client client;

    private LinkClientHooks() {
    }

    public static void setClient(Minecraft minecraft, MinecraftAccount account) {
        client = new Client(minecraft, account);
    }

    public static @Nullable Client client() {
        return client;
    }

    public static Client requireClient() {
        Client current = client;
        if (current == null) {
            throw new IllegalStateException("NetherLink client hook is not registered");
        }
        return current;
    }

    public record Client(Minecraft minecraft, MinecraftAccount account) {
    }
}
