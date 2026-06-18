package cool.muyucloud.netherlink.teacon;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import cool.muyucloud.netherlink.teacon.card.blockentity.IntroCardRackBlockEntity;

import java.util.function.Supplier;

/** Common registry hooks shared between Fabric and NeoForge. */
public final class CommonReg {
    /** Set by platform init before blocks are constructed. */
    public static Supplier<BlockEntityType<SignBlockEntity>> SIGN_BLOCK_ENTITY = null;
    /** Set by NeoForge RegisterEvent before block entities are created. */
    public static Supplier<BlockEntityType<IntroCardRackBlockEntity>> RACK_BLOCK_ENTITY = null;

    private CommonReg() {}
}
