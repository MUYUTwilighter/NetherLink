package cool.muyucloud.netherlink.client;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.authlib.yggdrasil.response.PresenceStatus;
import com.mojang.serialization.JsonOps;
import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.account.data.Account;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class ClientPresenceBlocker {
    private static final Gson GSON = new Gson();
    private static final long RESCAN_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(5L);
    private static long lastScanNanos;
    private static @Nullable UUID blockedProfileId;
    private static boolean loggedBlock;

    private ClientPresenceBlocker() {
    }

    public static boolean shouldBlock(Minecraft minecraft, PresenceStatus status) {
        if (status == PresenceStatus.PLAYING_HOSTED_SERVER) {
            return false;
        }
        UUID currentProfileId = minecraft.getUser().getProfileId();
        UUID accountProfileId = getBlockedProfileId();
        boolean block = currentProfileId.equals(accountProfileId);
        if (block && !loggedBlock) {
            NliConstants.LOG.info("Blocking client presence {} for NetherLink hosted account {}", status, currentProfileId);
            loggedBlock = true;
        } else if (!block) {
            loggedBlock = false;
        }
        return block;
    }

    private static @Nullable UUID getBlockedProfileId() {
        long now = System.nanoTime();
        if (now - lastScanNanos < RESCAN_INTERVAL_NANOS) {
            return blockedProfileId;
        }
        lastScanNanos = now;
        blockedProfileId = scanBlockedProfileId();
        return blockedProfileId;
    }

    private static @Nullable UUID scanBlockedProfileId() {
        if (!Files.isDirectory(NliConstants.ACCOUNT_DIR)) {
            return null;
        }
        try (var files = Files.list(NliConstants.ACCOUNT_DIR)) {
            return files
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .map(ClientPresenceBlocker::readEnabledProfileId)
                .filter(profileId -> profileId != null)
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            NliConstants.LOG.warn("Failed to scan NetherLink account directory for client presence blocking", e);
            return null;
        }
    }

    private static @Nullable UUID readEnabledProfileId(java.nio.file.Path path) {
        try {
            JsonElement json = GSON.fromJson(Files.readString(path), JsonElement.class);
            Account account = Account.CODEC.codec().decode(JsonOps.INSTANCE, json).getOrThrow().getFirst();
            if (!account.isEnabled()) {
                return null;
            }
            return parseProfileId(account.getMcProfileId());
        } catch (Exception e) {
            NliConstants.LOG.warn("Failed to read NetherLink account file for client presence blocking: {}", path.getFileName(), e);
            return null;
        }
    }

    private static @Nullable UUID parseProfileId(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() == 32) {
            normalized = normalized.replaceFirst(
                "([0-9a-f]{8})([0-9a-f]{4})([0-9a-f]{4})([0-9a-f]{4})([0-9a-f]{12})",
                "$1-$2-$3-$4-$5"
            );
        }
        return UUID.fromString(normalized);
    }
}
