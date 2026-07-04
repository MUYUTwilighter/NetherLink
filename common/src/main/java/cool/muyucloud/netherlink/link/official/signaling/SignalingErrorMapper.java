package cool.muyucloud.netherlink.link.official.signaling;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import cool.muyucloud.netherlink.link.model.LinkPeerRoute;
import cool.muyucloud.netherlink.link.transport.SignalingException;
import org.jetbrains.annotations.Nullable;

public final class SignalingErrorMapper {
    private SignalingErrorMapper() {
    }

    public static SignalingException fromJsonRpc(@Nullable LinkPeerRoute peer, JsonRpcException error) {
        String dataCode = error.dataCode();
        String message = serviceMessage(error);
        if (dataCode != null) {
            return switch (dataCode) {
                case "MissingOrExpiredIdentity" -> new SignalingException.SignalingAuthException(message);
                case "UnknownPlayer" -> new SignalingException.UnknownPlayerException(peer, message);
                default -> new SignalingException.SignalingRejectedException(peer, message);
            };
        }
        return message.contains("not registered")
            ? new SignalingException.UnknownPlayerException(peer, message)
            : new SignalingException.SignalingRejectedException(peer, message);
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
