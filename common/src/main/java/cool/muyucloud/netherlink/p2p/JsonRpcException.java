package cool.muyucloud.netherlink.p2p;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

public final class JsonRpcException extends RuntimeException {
    private final int code;
    private final String serverMessage;
    private final @Nullable JsonElement data;

    public JsonRpcException(int code, String message, @Nullable JsonElement data) {
        super("JSON-RPC error " + code + ": " + message);
        this.code = code;
        this.serverMessage = message;
        this.data = data;
    }

    public int code() {
        return this.code;
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
