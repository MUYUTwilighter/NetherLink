package cool.muyucloud.netherlink.client;

import com.google.gson.JsonObject;
import cool.muyucloud.netherlink.NetherLinkConfig;
import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.link.model.LinkTerms;
import cool.muyucloud.netherlink.link.nli.NliV1Config;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;

final class ClientTermsConsent {
    private ClientTermsConsent() {
    }

    static synchronized boolean isAccepted(Minecraft minecraft, ResourceLocation backendId, LinkTerms terms) {
        JsonObject accepted = acceptedTerms(minecraft);
        return accepted.has(key(backendId, terms.language()))
            && terms.revision().equals(accepted.get(key(backendId, terms.language())).getAsString());
    }

    static synchronized void accept(Minecraft minecraft, ResourceLocation backendId, LinkTerms terms) {
        Path path = path(minecraft);
        JsonObject config = readNliConfig(path);
        JsonObject accepted = NliV1Config.acceptedTerms(config);
        accepted.addProperty(key(backendId, terms.language()), terms.revision());
        try {
            NliConstants.LOG.debug("Saving accepted backend terms to {}", path);
            NliV1Config.write(path, config);
        } catch (RuntimeException error) {
            throw new IllegalStateException("Unable to save accepted NetherLink terms", error);
        }
    }

    private static JsonObject acceptedTerms(Minecraft minecraft) {
        JsonObject accepted = NliV1Config.acceptedTerms(readNliConfig(path(minecraft)));
        if (!accepted.isEmpty()) {
            return accepted;
        }
        JsonObject legacy = NetherLinkConfig.read(NetherLinkConfig.path(minecraft.gameDirectory.toPath()));
        if (legacy.has(NliV1Config.ACCEPTED_TERMS_KEY) && legacy.get(NliV1Config.ACCEPTED_TERMS_KEY).isJsonObject()) {
            return legacy.getAsJsonObject(NliV1Config.ACCEPTED_TERMS_KEY);
        }
        return accepted;
    }

    private static Path path(Minecraft minecraft) {
        return NliV1Config.path(minecraft.gameDirectory.toPath());
    }

    private static JsonObject readNliConfig(Path path) {
        try {
            return NliV1Config.read(path);
        } catch (RuntimeException error) {
            NliConstants.LOG.warn("Unable to read accepted backend terms from {}; treating them as unaccepted", path, error);
            return new JsonObject();
        }
    }

    private static String key(ResourceLocation backendId, String language) {
        return backendId + "|" + language;
    }
}
