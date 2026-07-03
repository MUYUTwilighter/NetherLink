package cool.muyucloud.netherlink;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

public final class NetherLinkConfig {
    public static final String ACTIVE_SERVICE_KEY = "activeService";
    public static final String INSTANCE_NAME_KEY = "instanceName";

    private NetherLinkConfig() {
    }

    public static Path path(Path gameDirectory) {
        return gameDirectory.resolve("config").resolve("netherlink").resolve("config.json");
    }

    public static JsonObject read(Path path) {
        if (!Files.isRegularFile(path)) {
            return new JsonObject();
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException | RuntimeException error) {
            NliConstants.LOG.warn("Unable to read NetherLink configuration from {}; using defaults", path, error);
            return new JsonObject();
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
            throw new IllegalStateException("Unable to save NetherLink configuration to " + path, error);
        }
    }

    public static Optional<String> instanceName(Path gameDirectory) {
        return string(read(path(gameDirectory)), INSTANCE_NAME_KEY);
    }

    public static Optional<String> string(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return Optional.empty();
        }
        return sanitize(object.get(key).getAsString());
    }

    private static Optional<String> sanitize(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? Optional.empty() : Optional.of(trimmed);
    }
}
