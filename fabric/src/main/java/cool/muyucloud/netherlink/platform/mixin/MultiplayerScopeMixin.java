package cool.muyucloud.netherlink.platform.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(MinecraftServer.MultiplayerScope.class)
enum MultiplayerScopeMixin {
    NETHER_LINK_INTEGRATED_SERVER("integrated_server");

    @Shadow
    MultiplayerScopeMixin(String key) {
    }
}
