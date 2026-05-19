package cool.muyucloud.netherlink.account;

import com.mojang.authlib.yggdrasil.FriendsService;
import com.mojang.authlib.yggdrasil.YggdrasilEnvironment;
import com.mojang.authlib.yggdrasil.YggdrasilFriendsService;
import com.mojang.authlib.yggdrasil.request.JoinInfoUpdate;
import com.mojang.authlib.yggdrasil.response.PresenceStatus;
import cool.muyucloud.netherlink.account.data.Account;

import java.net.Proxy;
import java.util.Set;

public class PresencePublisher {
    public void publish(Account account) {
        presence(account, PresenceStatus.PLAYING_HOSTED_SERVER, new JoinInfoUpdate(null, Set.of()));
    }

    public void revoke(Account account) {
        presence(account, PresenceStatus.OFFLINE, null);
    }

    private void presence(Account account, PresenceStatus status, JoinInfoUpdate joinInfoUpdate) {
        String token = account.getMcToken();
        if (token == null || token.isBlank()) {
            throw new NetherLinkAuthException("Minecraft access token was not found");
        }

        FriendsService service = new YggdrasilFriendsService(token, Proxy.NO_PROXY, YggdrasilEnvironment.PROD.getEnvironment());
        service.presence(status.name(), joinInfoUpdate);
    }
}
