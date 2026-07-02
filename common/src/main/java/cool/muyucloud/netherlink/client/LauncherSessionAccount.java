package cool.muyucloud.netherlink.client;

import cool.muyucloud.netherlink.account.MinecraftAccount;
import net.minecraft.client.User;

public final class LauncherSessionAccount implements MinecraftAccount {
    private final User user;

    public LauncherSessionAccount(User user) {
        this.user = user;
    }

    @Override
    public String getMcToken() {
        return this.user.getAccessToken();
    }

    @Override
    public String getMcProfileId() {
        return this.user.getProfileId().toString();
    }

    @Override
    public String getMcProfileName() {
        return this.user.getName();
    }

    public boolean isUsable() {
        return !this.getMcToken().isBlank();
    }
}
