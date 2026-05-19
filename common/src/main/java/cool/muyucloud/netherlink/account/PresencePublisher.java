package cool.muyucloud.netherlink.account;

import com.mojang.authlib.yggdrasil.FriendsService;
import com.mojang.authlib.yggdrasil.YggdrasilEnvironment;
import com.mojang.authlib.yggdrasil.YggdrasilFriendsService;
import com.mojang.authlib.yggdrasil.request.JoinInfoUpdate;
import com.mojang.authlib.yggdrasil.response.PresenceStatus;
import com.mojang.authlib.yggdrasil.response.PresenceStatusDto;
import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.account.data.Account;

import java.net.Proxy;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class PresencePublisher {
    public Map<UUID, UUID> publish(Account account) {
        NliConstants.LOG.info("Publishing NetherLink presence as PLAYING_HOSTED_SERVER for {}", account.getMcProfileName());
        return presence(account, PresenceStatus.PLAYING_HOSTED_SERVER, new JoinInfoUpdate(null, Set.of()));
    }

    public void revoke(Account account) {
        NliConstants.LOG.info("Revoking NetherLink presence for {}", account.getMcProfileName());
        presence(account, PresenceStatus.OFFLINE, null);
    }

    private Map<UUID, UUID> presence(Account account, PresenceStatus status, JoinInfoUpdate joinInfoUpdate) {
        String token = account.getMcToken();
        if (token == null || token.isBlank()) {
            throw new NetherLinkAuthException("Minecraft access token was not found");
        }

        FriendsService service = new YggdrasilFriendsService(token, Proxy.NO_PROXY, YggdrasilEnvironment.PROD.getEnvironment());
        Map<UUID, UUID> peers = service.presence(status.name(), joinInfoUpdate).presence().stream()
            .filter(presence -> presence.pmid() != null && presence.profileId() != null)
            .collect(Collectors.toUnmodifiableMap(PresenceStatusDto::pmid, PresenceStatusDto::profileId, (left, right) -> left));
        NliConstants.LOG.info("Presence {} returned {} peer mappings", status, peers.size());
        return peers;
    }
}
