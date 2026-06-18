package cool.muyucloud.netherlink.teacon.card.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Standing Intro Card Rack — sits on the ground like a signpost.
 */
public class StandingIntroCardRackBlock extends IntroCardRackBlock {

    public static final MapCodec<StandingIntroCardRackBlock> CODEC =
        simpleCodec(p -> new StandingIntroCardRackBlock(p));

    public static BlockBehaviour.Properties props() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD).sound(SoundType.WOOD)
            .strength(1.0F).noCollision();
    }

    public StandingIntroCardRackBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
}
