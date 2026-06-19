package cool.muyucloud.netherlink.teacon.block;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SignText;
import cool.muyucloud.netherlink.teacon.ModItems;
import cool.muyucloud.netherlink.teacon.entity.DoubleSidedSignBlockEntity;
import cool.muyucloud.netherlink.teacon.item.FriendCardItem;
import cool.muyucloud.netherlink.teacon.item.IntroCardItem;
import org.slf4j.Logger;

import java.util.UUID;

public final class IntroCardSignLogic {

    private static final Logger LOGGER = LogUtils.getLogger();

    private IntroCardSignLogic() {}

    public static InteractionResult handle(ItemStack stack, Level level, BlockPos pos, Player player) {
        boolean isIntroCard = stack.getItem() instanceof IntroCardItem;
        boolean isFriendCard = stack.getItem() instanceof FriendCardItem;
        DoubleSidedSignBlockEntity ds = level.getBlockEntity(pos) instanceof DoubleSidedSignBlockEntity d ? d : null;
        boolean hasText = ds != null && ds.getEditor() != null;

//        LOGGER.info("[IntroCardSign] {} side={} handItem={} isIntroCard={} hasBE={} hasEditor={} editor={} player={}",
//            player.getName().getString(),
//            level.isClientSide() ? "CLIENT" : "SERVER",
//            player.getItemInHand(InteractionHand.MAIN_HAND).getItem(),
//            isIntroCard,
//            ds != null,
//            hasText,
//            ds != null ? ds.getEditor() : "null",
//            player.getUUID());

        // IntroCard + has text -> rejected, consume to prevent vanilla fallthrough
        if (isIntroCard && hasText) {
//            LOGGER.info("[IntroCardSign] => REJECT (card + has text)");
            return InteractionResult.CONSUME;
        }

        // IntroCard + empty -> consume, edit (server-side: auth + send open packet)
        if (isIntroCard) {
//            LOGGER.info("[IntroCardSign] => EDIT (card + empty)");
            if (!level.isClientSide()) {
                stack.consume(1, player);
                if (ds != null) {
                    ds.setAllowedPlayerEditor(player.getUUID());
                    player.openTextEdit(ds, true); // sends ClientboundOpenSignEditorPacket
                }
            }
            return InteractionResult.SUCCESS;
        }

        // FriendCard -> TODO: implement friend card logic
        if (isFriendCard) {
            return InteractionResult.CONSUME;
        }

        // Empty hand + has text -> check ownership, clear, return card
        if (stack.isEmpty() && hasText) {
            UUID editor = ds.getEditor();
            boolean isOwner = editor != null && player.getUUID().equals(editor);
//            LOGGER.info("[IntroCardSign] => CLEAR check isOwner={}", isOwner);
            if (!isOwner) {
//                LOGGER.info("[IntroCardSign] => BLOCKED (not editor) expected={} actual={}", editor, player.getUUID());
                if (!level.isClientSide()) {
                    level.playSound(null, pos, ds.getSignInteractionFailedSoundEvent(),
                        SoundSource.BLOCKS, 1.0F, 1.0F);
                }
                return InteractionResult.SUCCESS;
            }
//            LOGGER.info("[IntroCardSign] => CLEARING text + returning card");
            if (!level.isClientSide()) {
                ds.setText(new SignText(), true); // also clears recordedEditor via setText override
                ds.setEditor(null); // explicit safeguard
                var card = new ItemStack(ModItems.INTRO_CARD);
                if (!player.getInventory().add(card)) player.drop(card, false);
                level.playSound(null, pos, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT,
                    SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }

//        LOGGER.info("[IntroCardSign] => PASS (no action)");
        return ds != null ? InteractionResult.CONSUME : InteractionResult.PASS;
    }
}