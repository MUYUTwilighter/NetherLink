package cool.muyucloud.netherlink.account.data;

import com.google.gson.JsonObject;

public class Endpoint {
    private String deviceCode;
    private String userCode;
    private String verificationUri;
    private String verificationUriComplete;
    private long expiresIn;
    private long interval;
    private String message;

    public static Endpoint fromJson(JsonObject json) {
        Endpoint endpoint = new Endpoint();
        endpoint.deviceCode = json.get("device_code").getAsString();
        endpoint.userCode = json.get("user_code").getAsString();
        endpoint.verificationUri = json.get("verification_uri").getAsString();
        if (json.has("verification_uri_complete")) {
            endpoint.verificationUriComplete = json.get("verification_uri_complete").getAsString();
        }
        endpoint.expiresIn = json.get("expires_in").getAsLong();
        endpoint.interval = json.has("interval") ? json.get("interval").getAsLong() : 5L;
        endpoint.message = json.has("message") ? json.get("message").getAsString() : null;
        return endpoint;
    }

    public String getDeviceCode() {
        return deviceCode;
    }

    public String getUserCode() {
        return userCode;
    }

    public String getVerificationUri() {
        return verificationUri;
    }

    public String getVerificationUriComplete() {
        return verificationUriComplete;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public long getInterval() {
        return interval;
    }

    public String getMessage() {
        return message;
    }
}
