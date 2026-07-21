package cool.muyucloud.netherlink;

import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;

public class NliConstants {
    public static final String MOD_ID = "netherlink";
    public static final String MOD_NAME = "NetherLink";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);
    public static final Supplier<String> MS_CLIENT_ID = new Supplier<>() {
        private String val = null;

        @Override
        public String get() {
            if (val == null) {
                String custom = System.getProperty("NETHERLINK_CLIENT_ID");
                if (custom == null || custom.isBlank()) {
                    custom = System.getenv("NETHERLINK_CLIENT_ID");
                }
                if (custom == null) {
                    try (InputStream stream = NliConstants.class.getClassLoader().getResourceAsStream("microsoft-client-id")) {
                        if (stream == null) {
                            throw new IllegalStateException("Unable to locate microsoft-client-id");
                        }
                        val = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
                    } catch (IOException e) {
                        throw new IllegalStateException("Unable to read microsoft-client-id", e);
                    }
                } else val = custom.trim();
                if (val.isEmpty() || val.startsWith("${")) {
                    LOG.warn("Invalid NETHERLINK_CLIENT_ID environment variable provided, serverside feature will be disabled");
                }
            }
            return val;
        }
    };
    public static final Long TIMEOUT = 300000L;
    public static final Integer INTERVAL_TOKEN = 1200;
    public static final Path ACCOUNT_DIR = Path.of("netherlink/accounts");
    public static final NliCommand<CommandSourceStack> SERVER_COMMAND = new NliCommand<>();
    @Nullable
    public static volatile MinecraftServer server;
    @NotNull
    public static volatile Supplier<Path> gameDirectory = () -> Path.of(".").normalize();
    @NotNull
    public static volatile Supplier<Optional<String>> windowTitle = Optional::empty;
    public static volatile String platform = "Vanilla";

    public static String resolveInstanceName() {
        return NetherLinkConfig.instanceName(gameDirectory())
            .or(NliConstants::windowTitle)
            .or(NliConstants::serverMotd)
            .orElseGet(() -> "Minecraft %s %s".formatted(SharedConstants.getCurrentVersion().getName(), platform));
    }

    private static Path gameDirectory() {
        try {
            Path path = gameDirectory.get();
            return path == null ? Path.of("") : path;
        } catch (RuntimeException error) {
            LOG.debug("Unable to resolve NetherLink game directory; using current directory", error);
            return Path.of("");
        }
    }

    private static Optional<String> windowTitle() {
        try {
            return windowTitle.get().flatMap(NliConstants::sanitize);
        } catch (RuntimeException error) {
            LOG.debug("Unable to resolve Minecraft window title; using server MOTD fallback", error);
            return Optional.empty();
        }
    }

    private static Optional<String> serverMotd() {
        MinecraftServer current = server;
        return current == null ? Optional.empty() : sanitize(current.getMotd());
    }

    private static Optional<String> sanitize(@Nullable String value) {
        if (value == null) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? Optional.empty() : Optional.of(trimmed);
    }
}
