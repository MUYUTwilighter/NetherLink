package cool.muyucloud.netherlink.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.link.LinkService;
import cool.muyucloud.netherlink.link.LinkServices;
import cool.muyucloud.netherlink.link.model.LinkFriendSettings;
import cool.muyucloud.netherlink.link.nli.NliLinkService;
import cool.muyucloud.netherlink.link.nli.NliV1Config;
import cool.muyucloud.netherlink.link.official.OfficialLinkServiceProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

final class ClientLinkSettings {
    private static final Identifier LEGACY_OFFICIAL_ID = Identifier.fromNamespaceAndPath(NliConstants.MOD_ID, "moj_26_2_s8");
    static final List<Identifier> AVAILABLE_SERVICES = List.of(NliLinkService.ID, OfficialLinkServiceProvider.ID);

    private ClientLinkSettings() {
    }

    static Identifier activeService(Minecraft minecraft) {
        String configured = string(read(path(minecraft)), NliV1Config.ACTIVE_SERVICE_KEY, NliLinkService.ID.toString());
        try {
            Identifier id = Identifier.parse(configured);
            if (LEGACY_OFFICIAL_ID.equals(id)) {
                return OfficialLinkServiceProvider.ID;
            }
            return AVAILABLE_SERVICES.contains(id) ? id : NliLinkService.ID;
        } catch (RuntimeException ignored) {
            return NliLinkService.ID;
        }
    }

    static CompletableFuture<Void> use(Minecraft minecraft, Identifier serviceId) {
        JsonObject config = read(path(minecraft));
        config.addProperty(NliV1Config.ACTIVE_SERVICE_KEY, serviceId.toString());
        if (!config.has(NliV1Config.SERVER_KEY)) {
            config.addProperty(NliV1Config.SERVER_KEY, NliV1Config.DEFAULT_SERVER);
        }
        write(path(minecraft), config);
        return LinkServices.use(create(minecraft, serviceId));
    }

    static Component createName(Identifier serviceId) {
        return Component.translatable(serviceId.getNamespace() + ".link." + serviceId.getPath());
    }

    static void applyConfiguredService(Minecraft minecraft) {
        Identifier serviceId = activeService(minecraft);
        LinkService current = LinkServices.current();
        if (current.id().equals(serviceId) && !requiresNliReload(minecraft, current)) {
            return;
        }
        LinkServices.use(create(minecraft, serviceId)).join();
    }

    static void updateMinecraftSocialManager(Minecraft minecraft, LinkFriendSettings settings) {
        Object manager = minecraft.getPlayerSocialManager();
        invoke(manager, "setFriendListEnabled", settings.friendsEnabled());
        invoke(manager, "setAllowFriendRequests", settings.acceptInvites());
    }

    private static LinkService create(Minecraft minecraft, Identifier serviceId) {
        if (OfficialLinkServiceProvider.ID.equals(serviceId)) {
            return OfficialLinkServiceProvider.INSTANCE;
        }
        return new NliLinkService(NliV1Config.serverUri(path(minecraft)));
    }

    private static boolean requiresNliReload(Minecraft minecraft, LinkService current) {
        return current instanceof NliLinkService nli
            && !nli.baseUri().equals(NliV1Config.serverUri(path(minecraft)));
    }

    private static void invoke(Object target, String name, boolean value) {
        try {
            Method method = target.getClass().getMethod(name, boolean.class);
            method.invoke(target, value);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static Path path(Minecraft minecraft) {
        return NliV1Config.path(minecraft.gameDirectory.toPath());
    }

    private static JsonObject read(Path path) {
        if (!Files.isRegularFile(path)) {
            return new JsonObject();
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException | RuntimeException error) {
            NliConstants.LOG.warn("Unable to read NetherLink client settings from {}; using defaults", path, error);
            return new JsonObject();
        }
    }

    private static void write(Path path, JsonObject config) {
        try {
            Files.createDirectories(path.getParent());
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(config, writer);
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            throw new CompletionException(new IllegalStateException("Unable to save NetherLink client settings", error));
        }
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback;
    }

}
