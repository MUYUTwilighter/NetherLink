package cool.muyucloud.netherlink.teacon.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;
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
    private @Nullable String recordedEditorName;

    /** Used by {@link BlockEntityType.BlockEntitySupplier}. */
    public DoubleSidedSignBlockEntity(BlockPos pos, BlockState state) {
        super(determineType(state), pos, state);
    }

    @SuppressWarnings("unchecked")
    public DoubleSidedSignBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super((BlockEntityType<SignBlockEntity>) (Object) type, pos, state);
    }

    @Override
    public void setLevel(@NonNull Level level) {
        super.setLevel(level);
        if (!level.isClientSide() && this.recordedEditor != null && this.recordedEditorName == null && level.getServer() != null) {
            level.getServer().services().nameToIdCache().get(this.recordedEditor).ifPresent(profile -> {
                this.recordedEditorName = profile.name();
                this.setChanged();
            });
        }
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("editor", UUIDUtil.CODEC, recordedEditor);
        if (recordedEditorName != null) {
            output.putString("editor_name", recordedEditorName);
        }
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        recordedEditor = input.read("editor", UUIDUtil.CODEC).orElse(null);
        recordedEditorName = input.getString("editor_name").orElse(null);
    }

    /** Doubles front text to the back face. Editor recording is handled by IntroCardSignLogic. */
    @Override
    public boolean setText(@NonNull SignText text, boolean isFrontText) {
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

    public @Nullable String getEditorName() {
        return recordedEditorName;
    }

    /** Manually set or clear the recorded editor. */
    public void setEditor(@Nullable UUID editor) {
        this.recordedEditor = editor;
        if (editor == null) {
            this.recordedEditorName = null;
        }
        this.setChanged();
    }

    public void setEditor(UUID editor, String editorName) {
        this.recordedEditor = editor;
        this.recordedEditorName = editorName;
        this.setChanged();
    }

    @Override
    public boolean isValidBlockState(@NonNull BlockState state) {
        return true; // handled by determineType above
    }

    @SuppressWarnings("unchecked")
    private static BlockEntityType<SignBlockEntity> determineType(BlockState state) {
        var block = state.getBlock();
        if (block instanceof CeilingHangingSignBlock || block instanceof WallHangingSignBlock) {
            var type = cool.muyucloud.netherlink.teacon.CommonReg.HANGING_SIGN_BLOCK_ENTITY;
            if (type != null && type.get() != null) return type.get();
        } else {
            var type = cool.muyucloud.netherlink.teacon.CommonReg.SIGN_BLOCK_ENTITY;
            if (type != null && type.get() != null) return type.get();
        }
        return (BlockEntityType<SignBlockEntity>) (Object)
            BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(
                Identifier.fromNamespaceAndPath("minecraft", "sign"));
    }
}
