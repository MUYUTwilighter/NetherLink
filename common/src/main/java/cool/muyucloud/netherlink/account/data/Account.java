package cool.muyucloud.netherlink.account.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import cool.muyucloud.netherlink.NliConstants;

import java.util.Date;
import java.util.Optional;
import java.util.function.Function;

public class Account {
    public static final MapCodec<Account> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        Codec.BOOL.optionalFieldOf("enabled", true).forGetter(Account::isEnabled),
        optionalFieldOf(Codec.STRING, "msRefreshToken", Account::getMsRefreshToken),
        optionalFieldOf(Codec.STRING, "msToken", Account::getMsToken),
        Codec.LONG.optionalFieldOf("msExpireAt", 0L).forGetter(Account::getMsExpireAt),
        optionalFieldOf(Codec.STRING, "xboxToken", Account::getXboxToken),
        optionalFieldOf(Codec.STRING, "xboxUserHash", Account::getXboxUserHash),
        Codec.LONG.optionalFieldOf("xboxExpireAt", 0L).forGetter(Account::getXboxExpireAt),
        optionalFieldOf(Codec.STRING, "xstsToken", Account::getXstsToken),
        optionalFieldOf(Codec.STRING, "xstsUserHash", Account::getXstsUserHash),
        Codec.LONG.optionalFieldOf("xstsExpireAt", 0L).forGetter(Account::getXstsExpireAt),
        optionalFieldOf(Codec.STRING, "mcToken", Account::getMcToken),
        Codec.LONG.optionalFieldOf("mcExpireAt", 0L).forGetter(Account::getMcExpireAt),
        optionalFieldOf(Codec.STRING, "mcProfileId", Account::getMcProfileId),
        optionalFieldOf(Codec.STRING, "mcProfileName", Account::getMcProfileName),
        optionalFieldOf(Codec.STRING, "mcPmid", Account::getMcPmid)
    ).apply(instance, (enabled, msRefreshToken, msToken, msExpireAt,
                       xboxToken, xboxUserHash, xboxExpireAt, xstsToken, xstsUserHash, xstsExpireAt,
                       mcToken, mcExpireAt, mcProfileId, mcProfileName, mcPmid) -> {
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
        account.setMcPmid(mcPmid.orElse(null));
        return account;
    }));

    private static <T, F> RecordCodecBuilder<T, Optional<F>> optionalFieldOf(Codec<F> codec, String key, Function<T, F> getter) {
        MapCodec<Optional<F>> field = codec.optionalFieldOf(key);
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
    private String mcPmid;

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

    public synchronized String getMcPmid() {
        return mcPmid;
    }

    public synchronized void setMcPmid(String mcPmid) {
        this.mcPmid = mcPmid;
    }
}
