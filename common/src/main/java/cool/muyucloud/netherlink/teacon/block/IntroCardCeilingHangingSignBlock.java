package cool.muyucloud.netherlink.teacon.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import cool.muyucloud.netherlink.teacon.CommonReg;
import cool.muyucloud.netherlink.teacon.entity.DoubleSidedSignBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.phys.BlockHitResult;

public class IntroCardCeilingHangingSignBlock extends CeilingHangingSignBlock {
    public static final MapCodec<CeilingHangingSignBlock> CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(WoodType.CODEC.fieldOf("wood_type").forGetter(CeilingHangingSignBlock::type),
                     BlockBehaviour.propertiesCodec()).apply(i, IntroCardCeilingHangingSignBlock::new));

    public IntroCardCeilingHangingSignBlock(WoodType woodType, Properties properties) { super(woodType, properties); }
    @Override public MapCodec<CeilingHangingSignBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DoubleSidedSignBlockEntity(CommonReg.HANGING_SIGN_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                           BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return IntroCardSignLogic.handle(stack, level, pos, player);
    }
}
