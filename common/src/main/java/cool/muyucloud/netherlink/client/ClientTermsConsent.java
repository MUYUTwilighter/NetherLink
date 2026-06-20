package cool.muyucloud.netherlink.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.link.model.LinkTerms;
import cool.muyucloud.netherlink.link.nli.NliV1Config;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class ClientTermsConsent {
    private ClientTermsConsent() {
    }

    static synchronized boolean isAccepted(Minecraft minecraft, Identifier backendId, LinkTerms terms) {
        JsonObject accepted = acceptedTerms(read(path(minecraft)));
        return accepted.has(key(backendId, terms.language()))
            && terms.revision().equals(accepted.get(key(backendId, terms.language())).getAsString());
    }

    static synchronized void accept(Minecraft minecraft, Identifier backendId, LinkTerms terms) {
        Path path = path(minecraft);
        JsonObject config = read(path);
        if (!config.has(NliV1Config.SERVER_KEY)) {
            config.addProperty(NliV1Config.SERVER_KEY, NliV1Config.DEFAULT_SERVER);
        }
        JsonObject accepted = acceptedTerms(config);
        accepted.addProperty(key(backendId, terms.language()), terms.revision());
        try {
            Files.createDirectories(path.getParent());
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                NliConstants.LOG.debug("Saving accepted backend terms to {}", path);
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(config, writer);
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Unable to save accepted NetherLink terms", error);
        }
    }

    private static JsonObject read(Path path) {
        if (!Files.isRegularFile(path)) {
            return new JsonObject();
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException | RuntimeException error) {
            NliConstants.LOG.warn("Unable to read accepted backend terms from {}; treating them as unaccepted", path, error);
            return new JsonObject();
        }
    }

    private static Path path(Minecraft minecraft) {
        return NliV1Config.path(minecraft.gameDirectory.toPath());
    }

    private static JsonObject acceptedTerms(JsonObject config) {
        if (config.has(NliV1Config.ACCEPTED_TERMS_KEY) && config.get(NliV1Config.ACCEPTED_TERMS_KEY).isJsonObject()) {
            return config.getAsJsonObject(NliV1Config.ACCEPTED_TERMS_KEY);
        }
        JsonObject accepted = new JsonObject();
        config.add(NliV1Config.ACCEPTED_TERMS_KEY, accepted);
        return accepted;
    }

    private static String key(Identifier backendId, String language) {
        return backendId + "|" + language;
    }
}
