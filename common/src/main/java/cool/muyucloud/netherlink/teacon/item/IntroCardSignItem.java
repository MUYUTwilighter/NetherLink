package cool.muyucloud.netherlink.teacon.item;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SignItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class IntroCardSignItem extends SignItem {
    public IntroCardSignItem(Block standingBlock, Block wallBlock, Properties properties) {
        super(standingBlock, wallBlock, properties);
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, Player player,
                                                   ItemStack stack, BlockState state) {
        return true;
    }
}

