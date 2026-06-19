package cool.muyucloud.netherlink.teacon.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import cool.muyucloud.netherlink.teacon.entity.DoubleSidedSignBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;
import cool.muyucloud.netherlink.teacon.CommonReg;

/** Wall sign that uses our shared BlockEntityType. */
public class TeaconWallSignBlock extends WallSignBlock {
    public static final MapCodec<WallSignBlock> CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(
            WoodType.CODEC.fieldOf("wood_type").forGetter(WallSignBlock::type),
            BlockBehaviour.propertiesCodec()
        ).apply(i, TeaconWallSignBlock::new));

    public TeaconWallSignBlock(WoodType woodType, BlockBehaviour.Properties properties) {
        super(woodType, properties);
    }

    @Override
    public MapCodec<WallSignBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        @SuppressWarnings("unchecked")
        var type = (BlockEntityType<SignBlockEntity>) (Object) CommonReg.SIGN_BLOCK_ENTITY.get();
        return new DoubleSidedSignBlockEntity(type, pos, state);
    }
}
