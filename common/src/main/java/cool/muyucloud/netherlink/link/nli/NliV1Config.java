package cool.muyucloud.netherlink.link.nli;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class NliV1Config {
    public static final String DEFAULT_SERVER = "https://nli-api.muyucloud.cool";
    public static final String SERVER_KEY = "server";
    public static final String ACCEPTED_TERMS_KEY = "acceptedTerms";

    private NliV1Config() {
    }

    public static Path path(Path gameDirectory) {
        return gameDirectory.resolve("config").resolve("netherlink").resolve("nli-v1.json");
    }

    public static URI serverUri(Path path) {
        JsonObject config = read(path);
        String configured = config.has(SERVER_KEY) && !config.get(SERVER_KEY).isJsonNull()
            ? config.get(SERVER_KEY).getAsString().trim()
            : DEFAULT_SERVER;
        URI uri;
        try {
            uri = URI.create(configured);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Invalid NLI v1 server URL in " + path, error);
        }
        if (!uri.isAbsolute() || (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalStateException("NLI v1 server URL in " + path + " must use http or https");
        }
        return uri;
    }

    public static JsonObject read(Path path) {
        if (!Files.isRegularFile(path)) {
            return new JsonObject();
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Unable to read NLI v1 configuration from " + path, error);
        }
    }

    public static void write(Path path, JsonObject config) {
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
            throw new IllegalStateException("Unable to save NLI v1 configuration to " + path, error);
        }
    }

    public static JsonObject acceptedTerms(JsonObject config) {
        if (config.has(ACCEPTED_TERMS_KEY) && config.get(ACCEPTED_TERMS_KEY).isJsonObject()) {
            return config.getAsJsonObject(ACCEPTED_TERMS_KEY);
        }
        JsonObject accepted = new JsonObject();
        config.add(ACCEPTED_TERMS_KEY, accepted);
        return accepted;
    }
}
