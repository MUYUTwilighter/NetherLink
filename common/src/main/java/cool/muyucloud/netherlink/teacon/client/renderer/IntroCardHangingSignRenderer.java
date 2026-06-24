package cool.muyucloud.netherlink.teacon.client.renderer;

import com.mojang.math.Transformation;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.HangingSignRenderer;
import net.minecraft.client.renderer.blockentity.state.HangingSignRenderState;
import net.minecraft.client.renderer.blockentity.state.SignRenderState;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** Hanging sign renderer with smaller text for IntroCard signs. */
public class IntroCardHangingSignRenderer extends HangingSignRenderer {

    private static final Transformation TEXT_SHRINK = new Transformation(
        new Matrix4f().scale(1.0F, 1.0F, 1.0F));

    public IntroCardHangingSignRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void extractRenderState(
        @NonNull SignBlockEntity blockEntity,
        @NonNull HangingSignRenderState state,
        float partialTicks,
        @NonNull Vec3 cameraPosition,
        net.minecraft.client.renderer.feature.ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress
    ) {
        super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        var t = state.transformations;
        state.transformations = new SignRenderState.SignTransformations(
            t.body(),
            t.frontText().compose(TEXT_SHRINK),
            t.backText().compose(TEXT_SHRINK)
        );
    }
}
