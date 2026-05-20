package cool.muyucloud.netherlink.account;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.stream.JsonWriter;
import com.mojang.serialization.JsonOps;
import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.access.Messenger;
import cool.muyucloud.netherlink.account.data.Account;
import cool.muyucloud.netherlink.p2p.ServerP2PManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class AccountManager {
    private static final Gson GSON = new Gson();
    private static final Duration SIGNALING_READY_TIMEOUT = Duration.ofSeconds(15L);
    private static final Map<String, Account> ACCOUNTS = new ConcurrentHashMap<>();
    private static final Map<String, AuthRequest> REQUESTS = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> PUBLISHED = new ConcurrentHashMap<>();
    private static final Map<String, ServerP2PManager> P2P = new ConcurrentHashMap<>();
    private static final PresencePublisher PRESENCE = new PresencePublisher();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "NetherLink Account");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean MAINTENANCE_PENDING = new AtomicBoolean();
    private static volatile MinecraftServer server;

    public static void start(MinecraftServer server) {
        AccountManager.server = server;
        load();
        publishAsync();
    }

    public static void stop(MinecraftServer server) {
        disconnectPlayersForShutdown(server);
        dump();
        revoke();
        clearRequests();
        if (AccountManager.server == server) {
            AccountManager.server = null;
        }
    }

    public static void add(Messenger messenger) {
        Account account = new Account();
        AuthRequest request = new AuthRequest(account, messenger);
        try {
            request.refreshMcProfileId(false);
            String profileName = account.getMcProfileName();
            if (profileName == null || profileName.isBlank()) {
                throw new NetherLinkAuthException("Minecraft profile name was not found");
            }
            account.setMcProfileName(profileName);
            ACCOUNTS.put(profileName, account);
            REQUESTS.put(profileName, request);
            dump(profileName);
            messenger.nli$sendMessage(() -> Component.literal("NetherLink account stored as \"" + profileName + "\"."));
        } catch (NetherLinkAuthException e) {
            NliConstants.LOG.warn("Failed to add NetherLink account", e);
            throw e;
        }
    }

    public static boolean remove(String name) {
        Account removed = ACCOUNTS.get(name);
        if (removed == null) {
            return false;
        }
        if (removed.isEnabled()) {
            revoke(name);
        }
        ACCOUNTS.remove(name);
        REQUESTS.remove(name);
        PUBLISHED.remove(name);
        stopP2P(name);
        deleteAccountFile(name);
        return true;
    }

    public static void refresh(@NotNull String name, boolean force, @NotNull Messenger messenger) {
        Account account = ACCOUNTS.get(name);
        if (account == null) {
            messenger.nli$sendMessage(() -> Component.literal("NetherLink account \"" + name + "\" was not found."));
            return;
        }
        if (!account.isEnabled()) {
            messenger.nli$sendMessage(() -> Component.literal("NetherLink account %s is disabled".formatted(name)));
            return;
        }
        AuthRequest request = REQUESTS.computeIfAbsent(name, _ -> new AuthRequest(account, messenger));
        if (!request.isPending()) {
            request.setMessenger(messenger);
        }
        request.refreshMcProfileId(force);
        if (account.getMcProfileName() == null || account.getMcProfileName().isBlank()) {
            REQUESTS.remove(name);
            throw new NetherLinkAuthException("Minecraft profile name was not found");
        }
        String profileName = account.getMcProfileName();
        if (!name.equals(profileName)) {
            ACCOUNTS.remove(name);
            ACCOUNTS.put(profileName, account);
            REQUESTS.remove(name);
            REQUESTS.put(profileName, request);
            if (PUBLISHED.remove(name) != null) {
                PUBLISHED.put(profileName, true);
            }
            ServerP2PManager manager = P2P.remove(name);
            if (manager != null) {
                P2P.put(profileName, manager);
            }
            deleteAccountFile(name);
        }
        dump(profileName);
    }

    public static void refresh(boolean force, Messenger messenger) {
        for (String name : new ArrayList<>(ACCOUNTS.keySet())) {
            refresh(name, force, messenger);
        }
    }

    public static void tick(int tickCount, Messenger messenger) {
        dumpMessages();
        boolean refresh = tickCount % NliConstants.INTERVAL_TOKEN == 0;
        boolean publish = tickCount % NliConstants.INTERVAL_PRESENCE == 0;
        if (refresh || publish) {
            submitMaintenance("maintain accounts", () -> {
                if (refresh) {
                    refresh(false, messenger);
                }
                if (publish) {
                    publish();
                }
            });
        }
    }

    public static void publishAsync() {
        submitMaintenance("publish accounts", AccountManager::publish);
    }

    public static void dumpMessages(String name) {
        AuthRequest request = REQUESTS.get(name);
        if (request == null) return;
        request.dumpMessages();
    }

    public static void dumpMessages() {
        REQUESTS.forEach((name, _) -> dumpMessages(name));
    }

    public static void clearRequests() {
        REQUESTS.clear();
        P2P.values().forEach(ServerP2PManager::shutdown);
        P2P.clear();
    }

    public static void disconnectPlayersForShutdown(MinecraftServer server) {
        if (server.getPlayerList() == null || server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        NliConstants.LOG.info("Disconnecting players before NetherLink P2P shutdown");
        server.getPlayerList().removeAll();
        try {
            Thread.sleep(1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void dump(String name) {
        Account account = ACCOUNTS.get(name);
        if (account == null) return;
        JsonElement json = Account.CODEC.codec().encodeStart(JsonOps.INSTANCE, account).getOrThrow();
        StringWriter writer = new StringWriter();
        GSON.toJson(json, new JsonWriter(writer));
        try {
            Files.createDirectories(NliConstants.ACCOUNT_DIR);
            Files.write(NliConstants.ACCOUNT_DIR.resolve(name + ".json"), writer.getBuffer().toString().getBytes());
        } catch (IOException e) {
            NliConstants.LOG.error("Failed to write account file: " + name + ".json");
        }
    }

    public static void dump() {
        ACCOUNTS.forEach((name, _) -> dump(name));
    }

    public static void load(String name) {
        try {
            String raw = Files.readString(NliConstants.ACCOUNT_DIR.resolve(name + ".json"));
            JsonElement json = GSON.fromJson(raw, JsonElement.class);
            Account account = Account.CODEC.codec().decode(JsonOps.INSTANCE, json)
                .getOrThrow().getFirst();
            String key = account.getMcProfileName() == null || account.getMcProfileName().isBlank() ? name : account.getMcProfileName();
            ACCOUNTS.put(key, account);
            REQUESTS.remove(name);
            REQUESTS.remove(key);
            PUBLISHED.remove(name);
            PUBLISHED.remove(key);
        } catch (Exception e) {
            NliConstants.LOG.error("Failed to read account file: " + name + ".json", e);
        }
    }

    public static void load() {
        try {
            if (!Files.exists(NliConstants.ACCOUNT_DIR)) {
                Files.createDirectories(NliConstants.ACCOUNT_DIR);
                return;
            }

            try (var files = Files.list(NliConstants.ACCOUNT_DIR)) {
                files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> load(path.getFileName().toString().replaceFirst("\\.json$", "")));
            }
        } catch (IOException e) {
            NliConstants.LOG.error("Failed to load account directory", e);
        }
    }

    public static void publish(String name) {
        Account account = ACCOUNTS.get(name);
        if (account == null || !account.isEnabled()) {
            return;
        }
        MinecraftServer currentServer = server;
        if (currentServer == null) {
            throw new NetherLinkAuthException("Minecraft server is not ready");
        }
        refresh(name, false, (Messenger)(Object)currentServer);
        ServerP2PManager manager = P2P.computeIfAbsent(name, key -> {
            NliConstants.LOG.info("Starting NetherLink P2P manager for account {}", key);
            ServerP2PManager created = new ServerP2PManager(key, account, currentServer);
            created.start();
            return created;
        });
        try {
            manager.awaitSignalingReady(SIGNALING_READY_TIMEOUT).join();
        } catch (CompletionException e) {
            throw new NetherLinkAuthException("Signaling did not become ready before publishing presence", e);
        }
        Map<java.util.UUID, java.util.UUID> presence = PRESENCE.publish(account);
        manager.updatePresence(presence);
        PUBLISHED.put(name, true);
        NliConstants.LOG.info("Published NetherLink account presence: {}", name);
    }

    public static void publish() {
        ACCOUNTS.forEach((s, _) -> publish(s));
    }

    public static void revoke(String name) {
        Account account = ACCOUNTS.get(name);
        if (account == null) {
            return;
        }
        if (account.getMcToken() != null && !account.getMcToken().isBlank()) {
            PRESENCE.revoke(account);
        }
        stopP2P(name);
        PUBLISHED.remove(name);
        NliConstants.LOG.info("Revoked NetherLink account presence: {}", name);
    }

    public static void revoke() {
        ACCOUNTS.forEach((s, _) -> revoke(s));
    }

    public static void setInability(String name, boolean enable) {
        Account account = ACCOUNTS.get(name);
        if (account == null) {
            return;
        }
        account.setEnabled(enable);
        if (!enable) {
            revoke(name);
        }
        dump(name);
    }

    public static void publish(String name, Messenger messenger) {
        if (!ACCOUNTS.containsKey(name)) {
            messenger.nli$sendMessage(() -> Component.literal("NetherLink account \"" + name + "\" was not found."));
            return;
        }
        publish(name);
        messenger.nli$sendMessage(() -> Component.literal("NetherLink account \"" + name + "\" published."));
    }

    public static void publish(Messenger messenger) {
        publish();
        messenger.nli$sendMessage(() -> Component.literal("Published NetherLink accounts."));
    }

    public static void revoke(String name, Messenger messenger) {
        if (!ACCOUNTS.containsKey(name)) {
            messenger.nli$sendMessage(() -> Component.literal("NetherLink account \"" + name + "\" was not found."));
            return;
        }
        revoke(name);
        messenger.nli$sendMessage(() -> Component.literal("NetherLink account \"" + name + "\" revoked."));
    }

    public static void revoke(Messenger messenger) {
        revoke();
        messenger.nli$sendMessage(() -> Component.literal("Revoked NetherLink accounts."));
    }

    public static void toggle(String name, Messenger messenger) {
        Account account = ACCOUNTS.get(name);
        if (account == null) {
            messenger.nli$sendMessage(() -> Component.literal("NetherLink account \"" + name + "\" was not found."));
            return;
        }
        boolean enabled = !account.isEnabled();
        setInability(name, enabled);
        messenger.nli$sendMessage(() -> Component.literal("NetherLink account \"" + name + "\" is now " + (enabled ? "enabled" : "disabled") + "."));
    }

    public static Collection<String> names() {
        return ACCOUNTS.keySet().stream().sorted().toList();
    }

    public static void list(Messenger messenger) {
        if (ACCOUNTS.isEmpty()) {
            messenger.nli$sendMessage(() -> Component.literal("No NetherLink accounts configured."));
            return;
        }

        messenger.nli$sendMessage(() -> Component.literal("NetherLink accounts:"));
        ACCOUNTS.entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> messenger.nli$sendMessage(() -> Component.literal(formatStatus(entry.getKey(), entry.getValue()))));
    }

    private static String formatStatus(String name, Account account) {
        return "- %s [%s] profile=%s pmid=%s mcToken=%s".formatted(
            name,
            (account.isEnabled() ? "enabled" : "disabled") + "," + (PUBLISHED.containsKey(name) ? "published" : "not published"),
            valueOrMissing(account.getMcProfileId()),
            valueOrMissing(account.getMcPmid()),
            tokenStatus(account.getMcExpireAt())
        );
    }

    private static String tokenStatus(Long expiresAt) {
        if (expiresAt == null || expiresAt <= 0L) {
            return "missing";
        }
        long remainingMillis = expiresAt - System.currentTimeMillis();
        if (remainingMillis <= 0L) {
            return "expired";
        }
        long minutes = remainingMillis / 60000L;
        return "valid, expires in " + minutes + "m";
    }

    private static String valueOrMissing(String value) {
        return value == null || value.isBlank() ? "<missing>" : value;
    }

    private static void deleteAccountFile(String name) {
        try {
            Files.deleteIfExists(NliConstants.ACCOUNT_DIR.resolve(name + ".json"));
        } catch (IOException e) {
            NliConstants.LOG.error("Failed to delete account file: " + name + ".json");
        }
    }

    private static void stopP2P(String name) {
        ServerP2PManager manager = P2P.remove(name);
        if (manager != null) {
            manager.shutdown();
        }
    }

    private static void submitMaintenance(String taskName, Runnable task) {
        if (!MAINTENANCE_PENDING.compareAndSet(false, true)) {
            return;
        }
        EXECUTOR.execute(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                NliConstants.LOG.warn("NetherLink maintenance failed: {}", taskName, e);
            } finally {
                MAINTENANCE_PENDING.set(false);
            }
        });
    }
}
