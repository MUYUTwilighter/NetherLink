package cool.muyucloud.netherlink.teacon.card.block;

import cool.muyucloud.netherlink.teacon.ModItems;
import cool.muyucloud.netherlink.teacon.card.blockentity.IntroCardRackBlockEntity;
import cool.muyucloud.netherlink.teacon.card.item.IntroCardItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Abstract base for Intro Card Rack blocks.
 * <p>
 * Subclasses define their own {@link #CODEC} and placement behavior.
 * Shared logic: attach/detach {@link IntroCardItem} via right-click,
 * managed by the {@link #HAS_CARD} blockstate property.
 */
public abstract class IntroCardRackBlock extends BaseEntityBlock {

    public static final BooleanProperty HAS_CARD = BooleanProperty.create("has_card");

    protected IntroCardRackBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HAS_CARD, false));
    }

    // ---- BlockEntity ----

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new IntroCardRackBlockEntity(pos, state);
    }

    // ---- Interaction ----

    @Override
    protected InteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos,
        Player player, InteractionHand hand, BlockHitResult hit) {

        boolean hasCard = state.getValue(HAS_CARD);
        var be = level.getBlockEntity(pos);

        if (!hasCard && stack.getItem() instanceof IntroCardItem) {
            // Handler: add card to rack
            if (!level.isClientSide()) {
                level.setBlock(pos, state.setValue(HAS_CARD, true), Block.UPDATE_ALL);
                stack.consume(1, player);
                if (be instanceof IntroCardRackBlockEntity rack) {
                    rack.incrementCounter();
                }
            }
            return InteractionResult.SUCCESS;

        } else if (hasCard && !(stack.getItem() instanceof IntroCardItem)) {
            // Handler: remove card from rack
            if (!level.isClientSide()) {
                level.setBlock(pos, state.setValue(HAS_CARD, false), Block.UPDATE_ALL);
                if (be instanceof IntroCardRackBlockEntity rack) {
                    rack.incrementCounter();
                }
                var cardStack = new ItemStack(ModItems.INTRO_CARD);
                if (!player.getInventory().add(cardStack)) {
                    player.drop(cardStack, false);
                }
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    // ---- BlockState Boilerplate ----

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HAS_CARD);
    }

    @Override
    public abstract MapCodec<? extends BaseEntityBlock> codec();
}
