package cool.muyucloud.netherlink.teacon.card.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;

/**
 * Wall-mounted Intro Card Rack — placed against a wall with a facing direction.
 */
public class WallIntroCardRackBlock extends IntroCardRackBlock {

    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final MapCodec<WallIntroCardRackBlock> CODEC =
        simpleCodec(p -> new WallIntroCardRackBlock(p));

    public static BlockBehaviour.Properties props() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD).sound(SoundType.WOOD)
            .strength(1.0F).noCollision();
    }

    public WallIntroCardRackBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }
}
