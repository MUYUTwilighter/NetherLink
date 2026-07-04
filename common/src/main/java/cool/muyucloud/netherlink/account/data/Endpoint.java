package cool.muyucloud.netherlink.account.data;

import com.google.gson.JsonObject;
import cool.muyucloud.netherlink.http.JsonHttp;

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
        endpoint.deviceCode = JsonHttp.requiredString(json, "device_code");
        endpoint.userCode = JsonHttp.requiredString(json, "user_code");
        endpoint.verificationUri = JsonHttp.requiredString(json, "verification_uri");
        endpoint.verificationUriComplete = JsonHttp.string(json, "verification_uri_complete");
        endpoint.expiresIn = JsonHttp.requiredLong(json, "expires_in");
        endpoint.interval = JsonHttp.longValue(json, "interval", 5L);
        endpoint.message = JsonHttp.string(json, "message");
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
