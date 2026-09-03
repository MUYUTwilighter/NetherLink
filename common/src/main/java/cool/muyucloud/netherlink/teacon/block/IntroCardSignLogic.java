package cool.muyucloud.netherlink.teacon.block;

import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.teacon.TeaconClientHooks;
import cool.muyucloud.netherlink.teacon.entity.DoubleSidedSignBlockEntity;
import cool.muyucloud.netherlink.teacon.item.FriendCardItem;
import cool.muyucloud.netherlink.teacon.item.IntroCardItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SignText;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class IntroCardSignLogic {
    private static final Path PLAYER_USAGE_PATH = Path.of("config/nli_player_usage.json");
    private static final Set<String> PLAYER_USAGE = new HashSet<>();
    private static final Set<UUID> CLEARABLE = Set.of(
        UUID.fromString("2e77afed-c0e6-4bb5-9b7b-a16e05211e74"),
        UUID.fromString("b54ce045-20a3-447b-93b8-522353ae3861"),
        UUID.fromString("1b301234-b981-4602-b1e2-f1598ce175fa"),
        UUID.fromString("7e1fdc94-a84d-4cca-b2fd-0877ef112041")
    );

    public static boolean validateFile(File file) {
        File parent = file.getParentFile();
        if (!parent.exists()) {
            try {
                Files.createDirectories(parent.toPath());
            } catch (IOException ex) {
                NliConstants.LOG.error("Cannot prepare parent directory for %s".formatted(file), ex);
                return false;
            }
        }
        if (!file.exists()) {
            try {
                Files.createFile(file.toPath());
                return true;
            } catch (IOException ex) {
                NliConstants.LOG.error("Cannot create file for %s".formatted(file), ex);
            }
        }
        return false;
    }

    public static void loadPlayerUsage() {
        if (!validateFile(PLAYER_USAGE_PATH.toFile())) return;
        try {
            List<String> lines = Files.readAllLines(PLAYER_USAGE_PATH);
            PLAYER_USAGE.clear();
            PLAYER_USAGE.addAll(lines);
        } catch (IOException e) {
            NliConstants.LOG.error("Cannot load player usage for %s".formatted(PLAYER_USAGE_PATH), e);
        }
    }

    public static void savePlayerUsage() {
        if (!validateFile(PLAYER_USAGE_PATH.toFile())) return;
        try {
            Files.write(PLAYER_USAGE_PATH, PLAYER_USAGE, StandardCharsets.UTF_8);
        } catch (IOException e) {
            NliConstants.LOG.error("Cannot save player usage for %s".formatted(PLAYER_USAGE_PATH), e);
        }
    }

    public static void removePlayerFromRecord(String playerName) {
        PLAYER_USAGE.remove(playerName);
    }

    public static void addPlayerToRecord(String playerName) {
        PLAYER_USAGE.add(playerName);
    }

    private IntroCardSignLogic() {}

    public static InteractionResult handle(ItemStack stack, Level level, BlockPos pos, Player player) {
        boolean isIntroCard = stack.getItem() instanceof IntroCardItem;
        boolean isFriendCard = stack.getItem() instanceof FriendCardItem;
        DoubleSidedSignBlockEntity ds = level.getBlockEntity(pos) instanceof DoubleSidedSignBlockEntity d ? d : null;
        boolean hasText = ds != null && ds.getEditor() != null;

        if (ds != null && stack.isEmpty() && player.isCrouching() && CLEARABLE.contains(player.getUUID())) {
            System.out.println("cleared");
            ds.setEditor(null);
            ds.setText(new SignText(), true);
            return InteractionResult.SUCCESS;
        }

        // IntroCard + has text -> rejected, consume to prevent vanilla fallthrough
        if (isIntroCard && hasText) {
            return InteractionResult.CONSUME;
        }

        // IntroCard + empty -> consume, edit (server-side: auth + send open packet)
        if (isIntroCard) {
            if (!level.isClientSide()) {
                if (PLAYER_USAGE.contains(player.getName().getString())) return InteractionResult.FAIL;
                stack.consume(1, player);
                if (ds != null) {
                    ds.setAllowedPlayerEditor(player.getUUID());
                    ds.setEditor(player.getUUID(), player.getGameProfile().name()); // assign ownership at card consumption
                    player.openTextEdit(ds, true); // sends ClientboundOpenSignEditorPacket
                }
            }
            return InteractionResult.SUCCESS;
        }

        // FriendCard + has text -> open confirmation screen (client), process request (server)
        if (isFriendCard && hasText) {
            UUID editor = ds.getEditor();
            if (player.getUUID().equals(editor)) {
                // Own sign -> rejected
                if (!level.isClientSide()) {
                    player.sendOverlayMessage(
                        Component.translatable("block.netherlink.friend_card.self_prompt"));
                }
                if (level.isClientSide()) {
                    player.playSound(ds.getSignInteractionFailedSoundEvent(), 1.0F, 1.0F);
                }
                return InteractionResult.CONSUME;
            }
            // Another player's sign
            if (level.isClientSide()) {
                TeaconClientHooks.openFriendCard(ds.getEditorName(), editor);
            }
            return InteractionResult.CONSUME;
        }

        // Empty hand + has text -> check ownership, clear, return card
        if (stack.isEmpty() && hasText) {
            UUID editor = ds.getEditor();
            boolean isOwner = player.getUUID().equals(editor);
//            LOGGER.info("[IntroCardSign] => CLEAR check isOwner={}", isOwner);
            if (!isOwner) {
//                LOGGER.info("[IntroCardSign] => BLOCKED (not editor) expected={} actual={}", editor, player.getUUID());
                if (!level.isClientSide()) {
                    level.playSound(null, pos, ds.getSignInteractionFailedSoundEvent(),
                        SoundSource.BLOCKS, 1.0F, 1.0F);
                }
                return InteractionResult.SUCCESS;
            }
            // Owner + has text → open menu screen (client), server waits for packet
            if (level.isClientSide()) {
                TeaconClientHooks.openOwnerMenu(pos);
            }
            return InteractionResult.CONSUME;
        }

//        LOGGER.info("[IntroCardSign] => PASS (no action)");
        return ds != null ? InteractionResult.CONSUME : InteractionResult.PASS;
    }
}
