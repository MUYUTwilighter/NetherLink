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
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.phys.BlockHitResult;

public class IntroCardStandingSignBlock extends StandingSignBlock {
    public static final MapCodec<StandingSignBlock> CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(WoodType.CODEC.fieldOf("wood_type").forGetter(StandingSignBlock::type),
                     BlockBehaviour.propertiesCodec()).apply(i, IntroCardStandingSignBlock::new));

    public IntroCardStandingSignBlock(WoodType woodType, Properties properties) { super(woodType, properties); }
    @Override public MapCodec<StandingSignBlock> codec() { return CODEC; }

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