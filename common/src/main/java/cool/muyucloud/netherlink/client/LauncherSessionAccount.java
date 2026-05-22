package cool.muyucloud.netherlink.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.account.MinecraftAccount;
import net.minecraft.client.User;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class LauncherSessionAccount implements MinecraftAccount {
    private final User user;
    private final @Nullable String pmid;

    public LauncherSessionAccount(User user) {
        this.user = user;
        this.pmid = extractPmid(user.getAccessToken());
    }

    @Override
    public @Nullable String getMcToken() {
        return this.user.getAccessToken();
    }

    @Override
    public @Nullable String getMcProfileId() {
        return this.user.getProfileId().toString();
    }

    @Override
    public @Nullable String getMcProfileName() {
        return this.user.getName();
    }

    @Override
    public @Nullable String getMcPmid() {
        return this.pmid;
    }

    public boolean isUsable() {
        return this.getMcToken() != null && !this.getMcToken().isBlank() && this.pmid != null && !this.pmid.isBlank();
    }

    private static @Nullable String extractPmid(String token) {
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        try {
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            return json.has("pmid") && !json.get("pmid").isJsonNull() ? json.get("pmid").getAsString() : null;
        } catch (RuntimeException e) {
            NliConstants.LOG.warn("Failed to parse PMID from launcher Minecraft access token", e);
            return null;
        }
    }
}
