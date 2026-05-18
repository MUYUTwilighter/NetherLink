package cool.muyucloud.netherlink.account;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.stream.JsonWriter;
import com.mojang.serialization.JsonOps;
import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.account.data.Account;
import cool.muyucloud.netherlink.access.Messenger;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class AccountManager {
    private static final Gson GSON = new Gson();
    private static final Map<String, Account> ACCOUNTS = new HashMap<>();
    private static final Map<String, AuthRequest> REQUESTS = new HashMap<>();

    public static void add(Messenger messenger) {
        Account account = new Account();
        AuthRequest request = new AuthRequest(account, messenger);
        try {
            request.refreshMcProfileId(false);
            String profileName = account.getMcProfileName();
            if (profileName == null || profileName.isBlank()) {
                throw new NetherLinkAuthException("Minecraft profile name was not found");
            }
            ACCOUNTS.put(profileName, account);
            REQUESTS.put(profileName, request);
            dump(profileName);
            messenger.cif$sendMessage(() -> Component.literal("NetherLink account stored as \"" + profileName + "\"."));
        } catch (NetherLinkAuthException e) {
            NliConstants.LOG.warn("Failed to add NetherLink account", e);
            throw e;
        }
    }

    public static void remove(String name) {
        revoke(name);
        ACCOUNTS.remove(name);
        REQUESTS.remove(name);
        try {
            Files.deleteIfExists(NliConstants.ACCOUNT_DIR.resolve(name + ".json"));
        } catch (IOException e) {
            NliConstants.LOG.error("Failed to delete account file: " + name + ".json");
        }
    }

    public static void refresh(@NotNull String name, boolean force, @NotNull Messenger messenger) {
        Account account = ACCOUNTS.get(name);
        if (account == null) {
            messenger.cif$sendMessage(() -> Component.literal("NetherLink account \"" + name + "\" was not found."));
            return;
        }
        if (!account.isEnabled()) {
            messenger.cif$sendMessage(() -> Component.literal("NetherLink account %s is disabled".formatted(name)));
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
        }
        dump(profileName);
    }

    public static void refresh(boolean force, Messenger messenger) {
        ACCOUNTS.forEach((name, _) -> refresh(name, force, messenger));
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
    }

    public static void dump(String name) {
        Account account = ACCOUNTS.get(name);
        if (account == null) return;
        JsonElement json = Account.CODEC.codec().encodeStart(JsonOps.COMPRESSED, account).getOrThrow();
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
            Account account = Account.CODEC.codec().decode(JsonOps.COMPRESSED, json).getOrThrow().getFirst();
            ACCOUNTS.put(name, account);
            REQUESTS.remove(name);
        } catch (Exception e) {
            NliConstants.LOG.error("Failed to read account file: " + name + ".json");
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
        // TODO publish presence to p2p server
    }

    public static void publish() {
        ACCOUNTS.forEach((s, _) -> publish(s));
    }

    public static void revoke(String name) {
        Account account = ACCOUNTS.get(name);
        if (account == null || !account.isEnabled()) {
            return;
        }
        // TODO revoke presence to p2p server
    }

    public static void revoke() {
        ACCOUNTS.forEach((s, _) -> revoke(s));
    }

    public static void setInability(String name, boolean enable) {
        Account account = ACCOUNTS.get(name);
        if (account == null || !account.isEnabled()) {
            return;
        }
        account.setEnabled(enable);
        if (!enable) {
            revoke(name);
        }
    }
}
