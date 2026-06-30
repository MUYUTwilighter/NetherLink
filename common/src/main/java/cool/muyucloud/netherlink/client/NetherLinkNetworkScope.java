package cool.muyucloud.netherlink.client;

import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

public enum NetherLinkNetworkScope {
    OFF("menu.multiplayerOptions.network.off", "menu.multiplayerOptions.network.off.tooltip"),
    LAN("menu.multiplayerOptions.network.lan", "menu.multiplayerOptions.network.lan.tooltip"),
    NETHERLINK("netherlink.multiplayer.scope", "netherlink.multiplayer.scope.tooltip");

    private final Component displayName;
    private final Component tooltip;

    NetherLinkNetworkScope(String translationKey, String tooltipKey) {
        this.displayName = Component.translatable(translationKey);
        this.tooltip = Component.translatable(tooltipKey);
    }

    public Component displayName() {
        return this.displayName;
    }

    public Component tooltip() {
        return this.tooltip;
    }

    public MinecraftServer.MultiplayerScope vanillaScope() {
        return this == LAN ? MinecraftServer.MultiplayerScope.LAN : MinecraftServer.MultiplayerScope.OFF;
    }

    public static NetherLinkNetworkScope current(IntegratedServer server) {
        if (ClientP2PController.isFriendsOpen(server)) {
            return NETHERLINK;
        }
        return server.getMultiplayerScope() == MinecraftServer.MultiplayerScope.LAN ? LAN : OFF;
    }
}
