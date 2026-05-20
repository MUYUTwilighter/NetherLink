package cool.muyucloud.netherlink;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.dedicated.DedicatedServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
                    throw new IllegalStateException("Please specify a valid NETHERLINK_CLIENT_ID in environment variable");
                }
            }
            return val;
        }
    };
    public static final Long TIMEOUT = 300000L;
    public static final Integer INTERVAL_TOKEN = 1200;
    public static final Integer INTERVAL_PRESENCE = 200;
    public static final Path ACCOUNT_DIR = Path.of("netherlink/accounts");
    public static final NliCommand<CommandSourceStack> SERVER_COMMAND = new NliCommand<>();
    public static DedicatedServer SERVER;
}
