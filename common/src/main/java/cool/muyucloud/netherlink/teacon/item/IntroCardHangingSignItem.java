package cool.muyucloud.netherlink.teacon.item;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SignItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class IntroCardHangingSignItem extends SignItem {
    public IntroCardHangingSignItem(Block ceilingBlock, Block wallBlock, Properties properties) {
        super(ceilingBlock, wallBlock, properties);
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, Player player,
                                                   ItemStack stack, BlockState state) {
        return true;
    }
}
