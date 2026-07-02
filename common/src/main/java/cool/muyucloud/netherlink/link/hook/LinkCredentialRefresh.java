package cool.muyucloud.netherlink.link.hook;

/** Environment capability for synchronously refreshing the bound account's Minecraft credentials. */
@FunctionalInterface
public interface LinkCredentialRefresh {
    /** Refreshes credentials or throws when refresh cannot be completed. */
    void refresh();
}
