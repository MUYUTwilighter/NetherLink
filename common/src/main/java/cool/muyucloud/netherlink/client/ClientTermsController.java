package cool.muyucloud.netherlink.client;

import cool.muyucloud.netherlink.link.LinkServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class ClientTermsController {
    private ClientTermsController() {
    }

    /** Runs an action only after the active backend's current terms have been accepted. */
    public static void runAfterAcceptance(Minecraft minecraft, Screen parent, Runnable action) {
        minecraft.execute(() -> minecraft.setScreen(new NetherLinkTermsScreen(parent, LinkServices.current(), action)));
    }
}
