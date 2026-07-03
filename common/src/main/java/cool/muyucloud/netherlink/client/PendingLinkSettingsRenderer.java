package cool.muyucloud.netherlink.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

final class PendingLinkSettingsRenderer implements LinkSettingsRenderer {
    private final Minecraft minecraft;
    private final ResourceLocation target;
    private final StringWidget message;

    PendingLinkSettingsRenderer(Minecraft minecraft, ResourceLocation target) {
        this.minecraft = minecraft;
        this.target = target;
        this.message = new StringWidget(
            Component.translatable(
                "netherlink.friends.settings.api.pending",
                ClientLinkSettings.serviceName(target)
            ).withStyle(ChatFormatting.GRAY),
            minecraft.font
        );
    }

    @Override
    public void visitChildren(Consumer<AbstractWidget> consumer) {
        consumer.accept(this.message);
    }

    @Override
    public void doLayout(ScreenRectangle area) {
        this.message.setPosition(
            area.left() + (area.width() - this.minecraft.font.width(this.message.getMessage())) / 2,
            area.top() + 24
        );
    }
}
