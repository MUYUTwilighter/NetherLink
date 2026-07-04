package cool.muyucloud.netherlink.link.official.signaling;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

public enum JsonRPCErrors {
    PARSE_ERROR(-32700, "Parse error"),
    INVALID_REQUEST(-32600, "Invalid Request"),
    METHOD_NOT_FOUND(-32601, "Method not found"),
    INVALID_PARAMS(-32602, "Invalid params"),
    INTERNAL_ERROR(-32603, "Internal error");

    private final int errorCode;
    private final String message;

    JsonRPCErrors(int errorCode, String message) {
        this.errorCode = errorCode;
        this.message = message;
    }

    JsonObject createWithUnknownId(@Nullable String data) {
        return JsonRPCUtils.createError(JsonNull.INSTANCE, this.message, this.errorCode, data);
    }

    JsonObject createWithoutData(JsonElement id) {
        return JsonRPCUtils.createError(id, this.message, this.errorCode, null);
    }

    JsonObject create(JsonElement id, String data) {
        return JsonRPCUtils.createError(id, this.message, this.errorCode, data);
    }
}
