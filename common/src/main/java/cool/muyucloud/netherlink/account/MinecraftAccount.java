package cool.muyucloud.netherlink.account;

import org.jspecify.annotations.Nullable;

public interface MinecraftAccount {
    @Nullable
    String getMcToken();

    @Nullable
    String getMcProfileId();

    @Nullable
    String getMcProfileName();

    @Nullable
    String getMcPmid();
}
