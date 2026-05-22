package cool.muyucloud.netherlink.client;

public final class ClientLanSettings {
    private static volatile boolean friendsOpen;

    private ClientLanSettings() {
    }

    public static boolean friendsOpen() {
        return friendsOpen;
    }

    public static void setFriendsOpen(boolean value) {
        friendsOpen = value;
    }
}
