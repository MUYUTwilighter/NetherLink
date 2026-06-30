package cool.muyucloud.netherlink.client;

public interface NetherLinkIntegratedServer {
    boolean nli$isFriendsOpen();

    void nli$setFriendsOpen(boolean friendsOpen);

    boolean nli$publishFriendsNetwork(int port);
}
