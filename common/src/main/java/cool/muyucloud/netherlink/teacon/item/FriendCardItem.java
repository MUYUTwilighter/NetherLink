package cool.muyucloud.netherlink.teacon.item;

import cool.muyucloud.netherlink.teacon.block.IntroCardSignLogic;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Friend Card item — right-click on an intro-card sign to initiate friend-related logic.
 */
public class FriendCardItem extends Item {

    public FriendCardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return IntroCardSignLogic.handle(
            context.getItemInHand(), context.getLevel(), context.getClickedPos(), context.getPlayer());
    }
}
