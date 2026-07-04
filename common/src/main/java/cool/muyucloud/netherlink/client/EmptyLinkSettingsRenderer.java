package cool.muyucloud.netherlink.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

final class EmptyLinkSettingsRenderer implements LinkSettingsRenderer {
    private final LinkSettingsContext context;
    private final StringWidget message;

    EmptyLinkSettingsRenderer(LinkSettingsContext context) {
        this.context = context;
        this.message = new StringWidget(
            Component.translatable("netherlink.friends.settings.empty").withStyle(ChatFormatting.GRAY),
            context.minecraft().font
        );
    }

    @Override
    public void visitChildren(Consumer<AbstractWidget> consumer) {
        consumer.accept(this.message);
    }

    @Override
    public void doLayout(ScreenRectangle area) {
        this.message.setPosition(
            area.left() + (area.width() - this.context.minecraft().font.width(this.message.getMessage())) / 2,
            area.top() + 24
        );
    }
}
