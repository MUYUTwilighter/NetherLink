package cool.muyucloud.netherlink.teacon;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SignBlockEntity;

import java.util.function.Supplier;

/** Common registry hooks shared between Fabric and NeoForge. */
public final class CommonReg {
    public static Supplier<BlockEntityType<SignBlockEntity>> SIGN_BLOCK_ENTITY = null;
    public static Supplier<BlockEntityType<SignBlockEntity>> HANGING_SIGN_BLOCK_ENTITY = null;

    private CommonReg() {}
}
