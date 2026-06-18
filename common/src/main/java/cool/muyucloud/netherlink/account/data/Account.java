package cool.muyucloud.netherlink.account.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.account.MinecraftAccount;

import java.util.Date;
import java.util.Optional;
import java.util.function.Function;

public class Account implements MinecraftAccount {
    public static final MapCodec<Account> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        Codec.BOOL.optionalFieldOf("enabled", true).forGetter(Account::isEnabled),
        optionalStringFieldOf("msRefreshToken", Account::getMsRefreshToken),
        optionalStringFieldOf("msToken", Account::getMsToken),
        Codec.LONG.optionalFieldOf("msExpireAt", 0L).forGetter(Account::getMsExpireAt),
        optionalStringFieldOf("xboxToken", Account::getXboxToken),
        optionalStringFieldOf("xboxUserHash", Account::getXboxUserHash),
        Codec.LONG.optionalFieldOf("xboxExpireAt", 0L).forGetter(Account::getXboxExpireAt),
        optionalStringFieldOf("xstsToken", Account::getXstsToken),
        optionalStringFieldOf("xstsUserHash", Account::getXstsUserHash),
        Codec.LONG.optionalFieldOf("xstsExpireAt", 0L).forGetter(Account::getXstsExpireAt),
        optionalStringFieldOf("mcToken", Account::getMcToken),
        Codec.LONG.optionalFieldOf("mcExpireAt", 0L).forGetter(Account::getMcExpireAt),
        optionalStringFieldOf("mcProfileId", Account::getMcProfileId),
        optionalStringFieldOf("mcProfileName", Account::getMcProfileName)
    ).apply(instance, (enabled, msRefreshToken, msToken, msExpireAt,
                       xboxToken, xboxUserHash, xboxExpireAt, xstsToken, xstsUserHash, xstsExpireAt,
                       mcToken, mcExpireAt, mcProfileId, mcProfileName) -> {
        Account account = new Account();
        account.setEnabled(enabled);
        account.setMsRefreshToken(msRefreshToken.orElse(null));
        account.setMsToken(msToken.orElse(null));
        account.setMsExpireAt(msExpireAt);
        account.setXboxToken(xboxToken.orElse(null));
        account.setXboxUserHash(xboxUserHash.orElse(null));
        account.setXboxExpireAt(xboxExpireAt);
        account.setXstsToken(xstsToken.orElse(null));
        account.setXstsUserHash(xstsUserHash.orElse(null));
        account.setXstsExpireAt(xstsExpireAt);
        account.setMcToken(mcToken.orElse(null));
        account.setMcExpireAt(mcExpireAt);
        account.setMcProfileId(mcProfileId.orElse(null));
        account.setMcProfileName(mcProfileName.orElse(null));
        return account;
    }));

    private static <T> RecordCodecBuilder<T, Optional<String>> optionalStringFieldOf(String key, Function<T, String> getter) {
        MapCodec<Optional<String>> field = Codec.STRING.optionalFieldOf(key);
        return field.forGetter(t -> Optional.ofNullable(getter.apply(t)));
    }

    private static long getCurrentTime() {
        return new Date().getTime();
    }

    private Boolean enabled = true;
    private String msRefreshToken;
    private String msToken;
    private Long msExpireAt = 0L;
    private String xboxToken;
    private String xboxUserHash;
    private Long xboxExpireAt = 0L;
    private String xstsToken;
    private String xstsUserHash;
    private Long xstsExpireAt = 0L;
    private String mcToken;
    private Long mcExpireAt = 0L;
    private String mcProfileId;
    private String mcProfileName;

    public synchronized boolean isEnabled() {
        return enabled;
    }

    public synchronized void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public synchronized String getMsRefreshToken() {
        return msRefreshToken;
    }

    public synchronized void setMsRefreshToken(String msRefreshToken) {
        this.msRefreshToken = msRefreshToken;
    }

    public synchronized boolean shouldRefreshMsToken() {
        return this.msToken == null || this.msExpireAt == null || this.msExpireAt - NliConstants.TIMEOUT <= getCurrentTime();
    }

    public synchronized String getMsToken() {
        return msToken;
    }

    public synchronized void setMsToken(String msToken) {
        this.msToken = msToken;
    }

    public synchronized Long getMsExpireAt() {
        return msExpireAt;
    }

    public synchronized void setMsExpireAt(Long msExpireAt) {
        this.msExpireAt = msExpireAt;
    }

    public synchronized boolean shouldRefreshXboxToken() {
        return this.xboxToken == null || this.xboxExpireAt == null || this.xboxExpireAt - NliConstants.TIMEOUT <= getCurrentTime();
    }

    public synchronized String getXboxToken() {
        return xboxToken;
    }

    public synchronized void setXboxToken(String xboxToken) {
        this.xboxToken = xboxToken;
    }

    public synchronized String getXboxUserHash() {
        return xboxUserHash;
    }

    public synchronized void setXboxUserHash(String xboxUserHash) {
        this.xboxUserHash = xboxUserHash;
    }

    public synchronized Long getXboxExpireAt() {
        return xboxExpireAt;
    }

    public synchronized void setXboxExpireAt(Long xboxExpireAt) {
        this.xboxExpireAt = xboxExpireAt;
    }

    public synchronized boolean shouldRefreshXstsToken() {
        return this.xstsToken == null || this.xstsExpireAt == null || this.xstsExpireAt - NliConstants.TIMEOUT <= getCurrentTime();
    }

    public synchronized String getXstsToken() {
        return xstsToken;
    }

    public synchronized void setXstsToken(String xstsToken) {
        this.xstsToken = xstsToken;
    }

    public synchronized String getXstsUserHash() {
        return xstsUserHash;
    }

    public synchronized void setXstsUserHash(String xstsUserHash) {
        this.xstsUserHash = xstsUserHash;
    }

    public synchronized Long getXstsExpireAt() {
        return xstsExpireAt;
    }

    public synchronized void setXstsExpireAt(Long xstsExpireAt) {
        this.xstsExpireAt = xstsExpireAt;
    }

    public synchronized boolean shouldRefreshMcToken() {
        return this.mcToken == null || this.mcExpireAt == null || this.mcExpireAt - NliConstants.TIMEOUT <= getCurrentTime();
    }

    public synchronized String getMcToken() {
        return mcToken;
    }

    public synchronized void setMcToken(String mcToken) {
        this.mcToken = mcToken;
    }

    public synchronized Long getMcExpireAt() {
        return mcExpireAt;
    }

    public synchronized void setMcExpireAt(Long mcExpireAt) {
        this.mcExpireAt = mcExpireAt;
    }

    public synchronized String getMcProfileId() {
        return mcProfileId;
    }

    public synchronized void setMcProfileId(String mcProfileId) {
        this.mcProfileId = mcProfileId;
    }

    public synchronized String getMcProfileName() {
        return mcProfileName;
    }

    public synchronized void setMcProfileName(String mcProfileName) {
        this.mcProfileName = mcProfileName;
    }

}
