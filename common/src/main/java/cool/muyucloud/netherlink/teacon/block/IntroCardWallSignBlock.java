package cool.muyucloud.netherlink.teacon.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.phys.BlockHitResult;
import cool.muyucloud.netherlink.teacon.CommonReg;
import cool.muyucloud.netherlink.teacon.entity.DoubleSidedSignBlockEntity;

public class IntroCardWallSignBlock extends WallSignBlock {
    public static final MapCodec<WallSignBlock> CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(WoodType.CODEC.fieldOf("wood_type").forGetter(WallSignBlock::type),
                     BlockBehaviour.propertiesCodec()).apply(i, IntroCardWallSignBlock::new));

    public IntroCardWallSignBlock(WoodType woodType, Properties properties) { super(woodType, properties); }
    @Override public MapCodec<WallSignBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DoubleSidedSignBlockEntity(CommonReg.SIGN_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                           BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return IntroCardSignLogic.handle(stack, level, pos, player);
    }
}