package cool.muyucloud.netherlink;

public final class ExternalNetwork {
    private ExternalNetwork() {
    }

    public static void logCurrentPrefs(String action) {
        NliConstants.LOG.info(
            "[P2P][network] {} using current network prefs preferIPv4Stack={} preferIPv6Addresses={}",
            action,
            System.getProperty("java.net.preferIPv4Stack"),
            System.getProperty("java.net.preferIPv6Addresses")
        );
    }
}
