package cool.muyucloud.netherlink.p2p;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public final class SignalingErrorMapper {
    private SignalingErrorMapper() {
    }

    public static SignalingException fromJsonRpc(@Nullable UUID peerPmid, JsonRpcException error) {
        String dataCode = error.dataCode();
        String message = serviceMessage(error);
        if (dataCode != null) {
            return switch (dataCode) {
                case "MissingOrExpiredIdentity" -> new SignalingException.SignalingAuthException(message);
                case "UnknownPlayer" -> new SignalingException.UnknownPlayerException(peerPmid, message);
                default -> new SignalingException.SignalingRejectedException(peerPmid, message);
            };
        }
        return message.contains("not registered")
            ? new SignalingException.UnknownPlayerException(peerPmid, message)
            : new SignalingException.SignalingRejectedException(peerPmid, message);
    }

    public static @Nullable SignalingException fromServiceEnvelope(@Nullable JsonElement body) {
        if (!(body instanceof JsonObject object) || !object.has("Code") || !object.get("Code").isJsonPrimitive()) {
            return null;
        }
        int code = object.get("Code").getAsInt();
        String message = serviceEnvelopeMessage(object);
        return switch (code) {
            case 1 -> new SignalingException.UnknownPlayerException(null, message);
            case 3 -> new SignalingException.TurnAuthFailedException(message);
            default -> new SignalingException.SignalingRejectedException(null, message);
        };
    }

    private static String serviceMessage(JsonRpcException error) {
        JsonElement data = error.data();
        if (data instanceof JsonObject object) {
            String message = serviceEnvelopeMessage(object);
            if (!message.isBlank()) {
                return message;
            }
        }
        return error.serverMessage();
    }

    private static String serviceEnvelopeMessage(JsonObject object) {
        JsonElement message = object.get("Message");
        return message != null && message.isJsonPrimitive() ? message.getAsString() : "";
    }
}
