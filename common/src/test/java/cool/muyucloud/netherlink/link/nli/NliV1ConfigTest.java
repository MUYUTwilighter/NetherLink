package cool.muyucloud.netherlink.link.nli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NliV1ConfigTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void usesDefaultServerWhenConfigIsMissing() {
        assertEquals(NliV1Config.DEFAULT_SERVER, NliV1Config.serverUri(this.temporaryDirectory.resolve("missing.json")).toString());
    }

    @Test
    void readsConfiguredServer() throws Exception {
        Path config = this.temporaryDirectory.resolve("nli-v1.json");
        Files.writeString(config, """
            {"server":"https://example.net/nli/"}
            """);

        assertEquals("https://example.net/nli/", NliV1Config.serverUri(config).toString());
    }

    @Test
    void rejectsNonHttpServer() throws Exception {
        Path config = this.temporaryDirectory.resolve("nli-v1.json");
        Files.writeString(config, """
            {"server":"file:///tmp/nli"}
            """);

        assertThrows(IllegalStateException.class, () -> NliV1Config.serverUri(config));
    }
}
