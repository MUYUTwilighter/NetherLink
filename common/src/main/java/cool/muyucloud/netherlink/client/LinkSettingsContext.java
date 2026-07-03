package cool.muyucloud.netherlink.client;

import cool.muyucloud.netherlink.link.LinkService;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

record LinkSettingsContext(
    Minecraft minecraft,
    LinkService service,
    Supplier<ClientFriendService> friendService,
    Consumer<Component> status,
    Runnable refresh,
    Runnable updateApplyState
) {
    void setStatus(Component component) {
        this.status.accept(component);
    }
}
