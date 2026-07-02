package cool.muyucloud.netherlink.link.official.signaling;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

final class JsonRPCUtils {
    private JsonRPCUtils() {
    }

    static JsonObject createSuccessResult(JsonElement id, JsonElement result) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);
        response.add("result", result);
        return response;
    }

    static JsonObject createError(JsonElement id, String message, int errorCode, @Nullable String data) {
        JsonObject errorResponse = new JsonObject();
        errorResponse.addProperty("jsonrpc", "2.0");
        errorResponse.add("id", id);
        JsonObject error = new JsonObject();
        error.addProperty("code", errorCode);
        error.addProperty("message", message);
        if (data != null && !data.isBlank()) {
            error.addProperty("data", data);
        }
        errorResponse.add("error", error);
        return errorResponse;
    }

    static @Nullable JsonElement getRequestId(JsonObject object) {
        return object.get("id");
    }

    static @Nullable String getMethodName(JsonObject object) {
        JsonElement method = object.get("method");
        return method == null || !method.isJsonPrimitive() ? null : method.getAsString();
    }

    static @Nullable JsonElement getParams(JsonObject object) {
        return object.get("params");
    }

    static @Nullable JsonElement getResult(JsonObject object) {
        return object.get("result");
    }

    static @Nullable JsonObject getError(JsonObject object) {
        JsonElement error = object.get("error");
        return error != null && error.isJsonObject() ? error.getAsJsonObject() : null;
    }
}
