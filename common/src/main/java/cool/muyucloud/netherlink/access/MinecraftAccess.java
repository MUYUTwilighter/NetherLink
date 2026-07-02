package cool.muyucloud.netherlink.access;

import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;

public interface MinecraftAccess {
    String nli$createWindowTitle();

    void nli$setPendingConnection(Connection connection);

    static MinecraftAccess get() {
        return (MinecraftAccess) Minecraft.getInstance();
    }

    static String createWindowTitle() {
        return get().nli$createWindowTitle();
    }

    static void setPendingConnection(Connection connection) {
        get().nli$setPendingConnection(connection);
    }
}
