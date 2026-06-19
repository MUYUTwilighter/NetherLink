package cool.muyucloud.netherlink.teacon.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Sign block entity that mirrors front text to the back face
 * and persistently records the UUID of the player who last edited it.
 * All intro-card signs use this to render text double-sided.
 */
public class DoubleSidedSignBlockEntity extends SignBlockEntity {

    /** UUID of the player who last wrote text on this sign. Persisted in NBT. */
    private @Nullable UUID recordedEditor;

    /** Used by {@link BlockEntityType.BlockEntitySupplier}. */
    public DoubleSidedSignBlockEntity(BlockPos pos, BlockState state) {
        super(fallbackSignType(), pos, state);
    }

    @SuppressWarnings("unchecked")
    public DoubleSidedSignBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super((BlockEntityType<SignBlockEntity>) (Object) type, pos, state);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("editor", UUIDUtil.CODEC, recordedEditor);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        recordedEditor = input.read("editor", UUIDUtil.CODEC).orElse(null);
    }

    /** Doubles front text to the back face. Editor recording is handled by IntroCardSignLogic. */
    @Override
    public boolean setText(SignText text, boolean isFrontText) {
        boolean result = super.setText(text, isFrontText);
        if (isFrontText) {
            super.setText(text, false);
            if (this.level != null && !this.level.isClientSide()) {
                this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
            }
        }
        return result;
    }

    /** Max width per text line in pixels. Doubled to fit more text. */
    @Override
    public int getMaxTextLineWidth() {
        return 115;
    }

    /** Vertical spacing between text lines. Larger for hanging signs due to wider layout. */
    @Override
    public int getTextLineHeight() {
        var block = this.getBlockState().getBlock();
        if (block instanceof net.minecraft.world.level.block.CeilingHangingSignBlock
            || block instanceof net.minecraft.world.level.block.WallHangingSignBlock) {
            return 16;
        }
        return 13;
    }

    /** @return UUID of the player who last edited this sign, or null. */
    public @Nullable UUID getEditor() {
        return recordedEditor;
    }

    /** Manually set or clear the recorded editor. */
    public void setEditor(@Nullable UUID editor) {
        this.recordedEditor = editor;
        this.setChanged();
    }

    @SuppressWarnings("unchecked")
    private static BlockEntityType<SignBlockEntity> fallbackSignType() {
        var type = cool.muyucloud.netherlink.teacon.CommonReg.SIGN_BLOCK_ENTITY;
        if (type != null && type.get() != null) {
            return type.get();
        }
        return (BlockEntityType<SignBlockEntity>) (Object)
            BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(
                Identifier.fromNamespaceAndPath("minecraft", "sign"));
    }
}