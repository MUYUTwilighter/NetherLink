package cool.muyucloud.netherlink.link.official.signaling;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

public final class JsonRpcException extends RuntimeException {
    private final String serverMessage;
    private final @Nullable JsonElement data;

    public JsonRpcException(int code, String message, @Nullable JsonElement data) {
        super("JSON-RPC error " + code + ": " + message);
        this.serverMessage = message;
        this.data = data;
    }

    public String serverMessage() {
        return this.serverMessage;
    }

    public @Nullable JsonElement data() {
        return this.data;
    }

    public @Nullable String dataCode() {
        if (this.data instanceof JsonObject object) {
            JsonElement code = object.get("Code");
            return code != null && code.isJsonPrimitive() ? code.getAsString() : null;
        }
        return null;
    }
}
