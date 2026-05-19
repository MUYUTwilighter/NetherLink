package cool.muyucloud.netherlink.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.access.Messenger;
import cool.muyucloud.netherlink.account.data.Account;
import cool.muyucloud.netherlink.account.data.Endpoint;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

public class AuthRequest {
    private static final URI MS_DEVICE_CODE = URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode");
    private static final URI MS_TOKEN = URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/token");
    private static final URI XBOX_AUTH = URI.create("https://user.auth.xboxlive.com/user/authenticate");
    private static final URI XSTS_AUTH = URI.create("https://xsts.auth.xboxlive.com/xsts/authorize");
    private static final URI MC_LOGIN = URI.create("https://api.minecraftservices.com/authentication/login_with_xbox");
    private static final URI MC_PROFILE = URI.create("https://api.minecraftservices.com/minecraft/profile");
    private static final String MS_SCOPE = "XboxLive.signin offline_access";

    private final Account account;
    private Messenger messenger;
    private final transient ConcurrentLinkedQueue<Supplier<Component>> messages = new ConcurrentLinkedQueue<>();
    private final transient HttpClient http = HttpClient.newHttpClient();
    private transient boolean pendingMsToken = false;
    private transient boolean pendingXboxToken = false;
    private transient boolean pendingXstsToken = false;
    private transient boolean pendingMcToken = false;
    private transient boolean pendingProfileId = false;

    public AuthRequest(Account account, Messenger messenger) {
        this.account = account;
        this.messenger = messenger;
    }

    public Account getAccount() {
        return account;
    }

    public void setMessenger(Messenger messenger) {
        this.messenger = messenger;
    }

    public boolean isPending() {
        return pendingMsToken || pendingXboxToken || pendingXstsToken || pendingMcToken || pendingProfileId;
    }

    public void dumpMessages() {
        Supplier<Component> message;
        while ((message = messages.poll()) != null) {
            messenger.cif$sendMessage(message);
        }
    }

    public void sendMessage(Supplier<Component> msg) {
        messages.offer(msg);
        messenger.cif$sendMessage(msg);
    }

    public void refreshMsToken(boolean force) throws NetherLinkAuthException {
        if (pendingMsToken || !(force || account.shouldRefreshMsToken())) return;
        pendingMsToken = true;
        try {
            if (account.getMsRefreshToken() == null || account.getMsRefreshToken().isBlank()) {
                requestMicrosoftTokenWithDeviceCode();
            } else {
                refreshMicrosoftToken();
            }
        } finally {
            pendingMsToken = false;
        }
        refreshXboxToken(force);
    }

    public void refreshXboxToken(boolean force) throws NetherLinkAuthException {
        if (pendingXboxToken || !(force || account.shouldRefreshXboxToken())) return;
        if (account.shouldRefreshMsToken()) {
            refreshMsToken(force);
            if (!account.shouldRefreshXboxToken()) return;
        }

        pendingXboxToken = true;
        try {
            JsonObject properties = new JsonObject();
            properties.addProperty("AuthMethod", "RPS");
            properties.addProperty("SiteName", "user.auth.xboxlive.com");
            properties.addProperty("RpsTicket", "d=" + account.getMsToken());

            JsonObject body = new JsonObject();
            body.add("Properties", properties);
            body.addProperty("RelyingParty", "http://auth.xboxlive.com");
            body.addProperty("TokenType", "JWT");

            JsonObject response = postJson(XBOX_AUTH, body);
            account.setXboxToken(requiredString(response, "Token"));
            account.setXboxExpireAt(parseNotAfter(response));
            account.setXboxUserHash(extractUserHash(response));
        } finally {
            pendingXboxToken = false;
        }
        refreshXstsToken(force);
    }

    public void refreshXstsToken(boolean force) throws NetherLinkAuthException {
        if (pendingXstsToken || !(force || account.shouldRefreshXstsToken())) return;
        if (account.shouldRefreshXboxToken()) {
            refreshXboxToken(force);
            if (!account.shouldRefreshXstsToken()) return;
        }

        pendingXstsToken = true;
        try {
            JsonArray userTokens = new JsonArray();
            userTokens.add(account.getXboxToken());

            JsonObject properties = new JsonObject();
            properties.addProperty("SandboxId", "RETAIL");
            properties.add("UserTokens", userTokens);

            JsonObject body = new JsonObject();
            body.add("Properties", properties);
            body.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
            body.addProperty("TokenType", "JWT");

            JsonObject response = postJson(XSTS_AUTH, body);
            account.setXstsToken(requiredString(response, "Token"));
            account.setXstsExpireAt(parseNotAfter(response));
            account.setXstsUserHash(extractUserHash(response));
        } finally {
            pendingXstsToken = false;
        }
        refreshMcToken(force);
    }

    public void refreshMcToken(boolean force) throws NetherLinkAuthException {
        if (pendingMcToken || !(force || account.shouldRefreshMcToken())) return;
        if (account.shouldRefreshXstsToken()) {
            refreshXstsToken(force);
            if (!account.shouldRefreshMcToken()) return;
        }

        pendingMcToken = true;
        try {
            String userHash = account.getXstsUserHash() != null ? account.getXstsUserHash() : account.getXboxUserHash();
            if (userHash == null || userHash.isBlank()) {
                throw new NetherLinkAuthException("Missing Xbox user hash");
            }

            JsonObject body = new JsonObject();
            body.addProperty("identityToken", "XBL3.0 x=" + userHash + ";" + account.getXstsToken());

            JsonObject response = postJson(MC_LOGIN, body);
            account.setMcToken(requiredString(response, "access_token"));
            account.setMcExpireAt(expiresAtFromSeconds(response.get("expires_in").getAsLong()));
        } finally {
            pendingMcToken = false;
        }
        refreshMcProfileId(force);
    }

    public void refreshMcProfileId(boolean force) throws NetherLinkAuthException {
        if (pendingProfileId || !(force || account.getMcProfileId() == null || account.getMcProfileName() == null))
            return;
        if (account.shouldRefreshMcToken()) {
            refreshMcToken(force);
            if (account.getMcProfileId() != null && account.getMcProfileName() != null) return;
        }

        pendingProfileId = true;
        try {
            JsonObject response = getJson(MC_PROFILE, Map.of("Authorization", "Bearer " + account.getMcToken()));
            account.setMcProfileId(requiredString(response, "id"));
            account.setMcProfileName(requiredString(response, "name"));
            sendMessage(() -> Component.literal("NetherLink account logged in as " + account.getMcProfileName() + "."));
        } finally {
            pendingProfileId = false;
        }
    }

    private void requestMicrosoftTokenWithDeviceCode() {
        Endpoint endpoint = Endpoint.fromJson(postForm(MS_DEVICE_CODE, Map.of(
            "client_id", NliConstants.MS_CLIENT_ID,
            "scope", MS_SCOPE
        )));
        sendDeviceCodeMessage(endpoint);

        long deadline = System.currentTimeMillis() + endpoint.getExpiresIn() * 1000L;
        long intervalMillis = endpoint.getInterval() * 1000L;
        while (System.currentTimeMillis() < deadline) {
            sleep(intervalMillis);
            HttpResult result = postFormRaw(MS_TOKEN, Map.of(
                "grant_type", "urn:ietf:params:oauth:grant-type:device_code",
                "client_id", NliConstants.MS_CLIENT_ID,
                "device_code", endpoint.getDeviceCode()
            ));
            if (result.isSuccess()) {
                applyMicrosoftTokenResponse(result.body());
                return;
            }

            String error = optionalString(result.body(), "error");
            if ("authorization_pending".equals(error)) {
                continue;
            }
            if ("slow_down".equals(error)) {
                intervalMillis += 5000L;
                continue;
            }
            if ("authorization_declined".equals(error) || "access_denied".equals(error)) {
                throw fail("Microsoft login was declined");
            }
            if ("expired_token".equals(error)) {
                throw fail("Microsoft login code expired");
            }
            throw fail("Microsoft login failed: " + describeError(result));
        }
        throw fail("Microsoft login code expired");
    }

    private void refreshMicrosoftToken() {
        HttpResult result = postFormRaw(MS_TOKEN, Map.of(
            "grant_type", "refresh_token",
            "client_id", NliConstants.MS_CLIENT_ID,
            "refresh_token", account.getMsRefreshToken(),
            "scope", MS_SCOPE
        ));
        if (!result.isSuccess()) {
            account.setMsRefreshToken(null);
            throw fail("Microsoft token refresh failed: " + describeError(result));
        }
        applyMicrosoftTokenResponse(result.body());
    }

    private void applyMicrosoftTokenResponse(JsonObject response) {
        account.setMsToken(requiredString(response, "access_token"));
        if (response.has("refresh_token")) {
            account.setMsRefreshToken(response.get("refresh_token").getAsString());
        }
        account.setMsExpireAt(expiresAtFromSeconds(response.get("expires_in").getAsLong()));
    }

    private void sendDeviceCodeMessage(Endpoint endpoint) {
        sendMessage(() -> Component.literal("NetherLink login started"));
        String uri = endpoint.getVerificationUriComplete() != null ? endpoint.getVerificationUriComplete() : endpoint.getVerificationUri();
        sendMessage(() -> Component.literal("Open: " + uri));
        sendMessage(() -> Component.literal("Code: " + endpoint.getUserCode()));
        sendMessage(() -> Component.literal("Expires in: " + endpoint.getExpiresIn() / 60L + " minutes"));
    }

    private JsonObject postForm(URI uri, Map<String, String> form) {
        HttpResult result = postFormRaw(uri, form);
        if (!result.isSuccess()) {
            throw fail("Request failed: " + describeError(result));
        }
        return result.body();
    }

    private HttpResult postFormRaw(URI uri, Map<String, String> form) {
        return send(HttpRequest.newBuilder(uri)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(encodeForm(form)))
            .build());
    }

    private JsonObject postJson(URI uri, JsonObject body) {
        HttpResult result = send(HttpRequest.newBuilder(uri)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build());
        if (!result.isSuccess()) {
            if (MC_LOGIN.equals(uri) && result.statusCode() == 403) {
                throw fail("Minecraft Services rejected Xbox login: " + describeError(result));
            }
            throw fail("Request failed: " + describeError(result));
        }
        return result.body();
    }

    private JsonObject getJson(URI uri, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).GET().header("Accept", "application/json");
        headers.forEach(builder::header);
        HttpResult result = send(builder.build());
        if (!result.isSuccess()) {
            throw fail("Request failed: " + describeError(result));
        }
        return result.body();
    }

    private HttpResult send(HttpRequest request) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonObject body = response.body() == null || response.body().isBlank()
                ? new JsonObject()
                : JsonParser.parseString(response.body()).getAsJsonObject();
            return new HttpResult(response.statusCode(), body);
        } catch (IOException e) {
            throw new NetherLinkAuthException("Network request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NetherLinkAuthException("Authentication request interrupted", e);
        } catch (RuntimeException e) {
            throw new NetherLinkAuthException("Failed to parse authentication response", e);
        }
    }

    private String encodeForm(Map<String, String> form) {
        StringBuilder builder = new StringBuilder();
        form.forEach((key, value) -> {
            if (!builder.isEmpty()) {
                builder.append('&');
            }
            builder.append(URLEncoder.encode(key, StandardCharsets.UTF_8));
            builder.append('=');
            builder.append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        });
        return builder.toString();
    }

    private long parseNotAfter(JsonObject response) {
        return Instant.parse(requiredString(response, "NotAfter")).toEpochMilli();
    }

    private String extractUserHash(JsonObject response) {
        return response.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();
    }

    private long expiresAtFromSeconds(long seconds) {
        return System.currentTimeMillis() + seconds * 1000L;
    }

    private String requiredString(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            throw fail("Authentication response missed field: " + key);
        }
        return object.get(key).getAsString();
    }

    private String optionalString(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : null;
    }

    private String describeError(HttpResult result) {
        String error = optionalString(result.body(), "error");
        String description = optionalString(result.body(), "error_description");
        String errorMessage = optionalString(result.body(), "errorMessage");
        String errorType = optionalString(result.body(), "errorType");
        String path = optionalString(result.body(), "path");
        if (error == null && errorMessage == null && errorType == null) {
            return "HTTP " + result.statusCode() + " " + result.body();
        }

        StringBuilder builder = new StringBuilder("HTTP ").append(result.statusCode()).append(": ");
        if (errorType != null) {
            builder.append(errorType).append(": ");
        }
        if (error != null) {
            builder.append(error);
        }
        if (errorMessage != null) {
            if (error != null) {
                builder.append(" - ");
            }
            builder.append(errorMessage);
        }
        if (description != null) {
            builder.append(" (").append(description).append(")");
        }
        if (path != null) {
            builder.append(" [").append(path).append("]");
        }
        return builder.toString();
    }

    private NetherLinkAuthException fail(String message) {
        sendMessage(() -> Component.literal("NetherLink login failed: " + message));
        return new NetherLinkAuthException(message);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NetherLinkAuthException("Authentication request interrupted", e);
        }
    }

    private record HttpResult(int statusCode, JsonObject body) {
        private boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300;
        }
    }
}
