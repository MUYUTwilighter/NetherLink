package cool.muyucloud.netherlink.teacon.item;

import cool.muyucloud.netherlink.teacon.block.IntroCardSignLogic;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Intro Card item — right-click on an intro-card sign to write text,
 * or on a sign with text to retrieve the card (owner only).
 */
public class IntroCardItem extends Item {

    public IntroCardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return IntroCardSignLogic.handle(
            context.getItemInHand(), context.getLevel(), context.getClickedPos(), context.getPlayer());
    }
}
