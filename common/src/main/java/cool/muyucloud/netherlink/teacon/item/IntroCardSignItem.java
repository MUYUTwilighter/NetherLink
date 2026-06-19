package cool.muyucloud.netherlink.teacon.item;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SignItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import cool.muyucloud.netherlink.teacon.ModBlocks;

public class IntroCardSignItem extends SignItem {
    public IntroCardSignItem(Properties properties) {
        super(ModBlocks.INTRO_CARD_STANDING_SIGN, ModBlocks.INTRO_CARD_WALL_SIGN, properties);
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, Player player,
                                                   ItemStack stack, BlockState state) {
        return true;
    }

}

