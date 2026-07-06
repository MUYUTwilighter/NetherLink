package cool.muyucloud.netherlink.client;

import cool.muyucloud.netherlink.NliConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

public final class NetherLinkIconButton extends Button {
    private static final int BUTTON_SIZE = 20;
    private static final int ICON_SIZE = 16;
    private static final int TEXTURE_SIZE = 64;
    private static final Identifier ICON = Identifier.tryBuild(NliConstants.MOD_ID, "textures/gui/icon.png");

    public NetherLinkIconButton(int x, int y, OnPress onPress) {
        super(x, y, BUTTON_SIZE, BUTTON_SIZE, Component.empty(), onPress, supplier -> Component.translatable("netherlink.friends.title"));
        this.setTooltip(Tooltip.create(Component.translatable("netherlink.friends.tooltip")));
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.extractDefaultSprite(graphics);
        int iconSize = Math.min(ICON_SIZE, Math.min(this.getWidth() - 4, this.getHeight() - 4));
        int x = this.getX() + (this.getWidth() - iconSize) / 2;
        int y = this.getY() + (this.getHeight() - iconSize) / 2;
        float tint = this.active ? 1.0F : 0.55F;
        int color = ARGB.colorFromFloat(this.alpha, tint, tint, tint);
        graphics.blit(RenderPipelines.GUI_TEXTURED, ICON, x, y, 0.0F, 0.0F, iconSize, iconSize, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, color);
    }
}