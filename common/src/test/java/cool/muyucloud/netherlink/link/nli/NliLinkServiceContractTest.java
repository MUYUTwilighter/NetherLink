package cool.muyucloud.netherlink.link.nli;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import cool.muyucloud.netherlink.account.MinecraftAccount;
import cool.muyucloud.netherlink.link.hook.LinkContextHooks;
import cool.muyucloud.netherlink.link.model.LinkFriendRelationship;
import cool.muyucloud.netherlink.link.model.LinkOfficialSyncStatus;
import cool.muyucloud.netherlink.link.model.LinkPresenceStatus;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class NliLinkServiceContractTest {
    @Test
    void registersRuntimeUsesInstanceTokenAndParsesMultiPresenceFriends() throws Exception {
        AtomicReference<String> registrationAuth = new AtomicReference<>();
        AtomicReference<String> renewalAuth = new AtomicReference<>();
        AtomicReference<String> friendsAuth = new AtomicReference<>();
        AtomicReference<String> friendsMinecraftAuth = new AtomicReference<>();
        AtomicReference<String> requestMinecraftAuth = new AtomicReference<>();
        AtomicReference<String> removeMinecraftAuth = new AtomicReference<>();
        AtomicReference<String> acceptedRequestPath = new AtomicReference<>();
        AtomicReference<String> deletedRequestPath = new AtomicReference<>();
        AtomicReference<String> closeAuth = new AtomicReference<>();
        AtomicReference<String> termsAuth = new AtomicReference<>();
        AtomicReference<String> termsLanguage = new AtomicReference<>();
        AtomicBoolean closed = new AtomicBoolean();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/instances", exchange -> {
            if (exchange.getRequestURI().getPath().endsWith("/renew")) {
                renewalAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
                respond(exchange, 200, """
                    {"profileId":"00000000-0000-0000-0000-000000000001","presenceId":"presence-client","instanceToken":"instance-renewed","expiresAt":"2099-02-01T00:00:00Z"}
                    """);
                return;
            }
            registrationAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, """
                {"profileId":"00000000-0000-0000-0000-000000000001","name":"Tester","presenceId":"presence-client","instanceToken":"instance-secret","expiresAt":"2099-01-01T00:00:00Z"}
                """);
        });
        server.createContext("/v1/terms", exchange -> {
            termsAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            termsLanguage.set(exchange.getRequestHeaders().getFirst("Accept-Language"));
            respondText(exchange, 200, "NLI terms");
        });
        server.createContext("/v1/friends", exchange -> {
            friendsAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            if ("DELETE".equals(exchange.getRequestMethod())) {
                removeMinecraftAuth.set(exchange.getRequestHeaders().getFirst("X-Minecraft-Access-Token"));
                respond(exchange, 204, "");
                return;
            }
            friendsMinecraftAuth.set(exchange.getRequestHeaders().getFirst("X-Minecraft-Access-Token"));
            respond(exchange, 200, """
                {
                  "friends":[{
                    "profileId":"00000000-0000-0000-0000-000000000002",
                    "name":"Friend",
                    "source":"minecraft_sync",
                    "presences":[
                      {"profileId":"00000000-0000-0000-0000-000000000002","presenceId":"friend-a","status":"HOSTING","joinable":true,"sessionId":null,"endpoint":null,"displayText":"World A","updatedAt":"2026-06-19T00:00:00Z","expiresAt":"2099-01-01T00:00:00Z"},
                      {"profileId":"00000000-0000-0000-0000-000000000002","presenceId":"friend-b","status":"ONLINE","joinable":false,"sessionId":null,"endpoint":null,"displayText":"Instance B","updatedAt":"2026-06-19T00:00:00Z","expiresAt":"2099-01-01T00:00:00Z"}
                    ]
                  }],
                  "incomingRequests":[{"profileId":"00000000-0000-0000-0000-000000000003","name":"Incoming","source":"minecraft_sync"}],
                  "outgoingRequests":[]
                }
                """);
        });
        server.createContext("/v1/friends/requests", exchange -> {
            String path = exchange.getRequestURI().getPath();
            requestMinecraftAuth.set(exchange.getRequestHeaders().getFirst("X-Minecraft-Access-Token"));
            if ("POST".equals(exchange.getRequestMethod())) {
                acceptedRequestPath.set(path);
                String relationship = "/v1/friends/requests".equals(path) ? "REQUESTED" : "ACCEPTED";
                respond(exchange, 200, "{\"result\":\"SUCCESS\",\"relationship\":\"" + relationship + "\",\"officialSync\":\"SUCCESS\"}");
            } else {
                deletedRequestPath.set(path);
                respond(exchange, 204, "");
            }
        });
        server.createContext("/v1/instances/current", exchange -> {
            closeAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            closed.set(true);
            respond(exchange, 204, "");
        });
        server.start();

        String runtimeKey = "test:nli:" + UUID.randomUUID();
        NliLinkService service = new NliLinkService(java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
        try {
            assertEquals("netherlink:nli_v1", service.id().toString());
            assertEquals(
                "netherlink.link.nli_v1",
                assertInstanceOf(TranslatableContents.class, service.name().getContents()).getKey()
            );
            var terms = service.terms("zh-CN").join().orElseThrow();
            assertEquals("zh-CN", terms.language());
            assertEquals("NLI terms", terms.text());
            assertFalse(terms.revision().isBlank());
            assertNull(termsAuth.get());
            assertEquals("zh-CN", termsLanguage.get());
            LinkContextHooks.setClientConnection(runtimeKey, new TestAccount(), "Test client", _ -> {});
            var identity = service.runtime().open(runtimeKey).join();
            service.runtime().renew(runtimeKey).join();
            var friends = service.createFriendService(runtimeKey);
            var snapshot = friends.refresh().join();
            UUID incomingId = UUID.fromString("00000000-0000-0000-0000-000000000003");
            var addOutcome = friends.add("TargetPlayer").join();
            var acceptOutcome = friends.accept(incomingId).join();
            friends.decline(incomingId).join();
            friends.remove(UUID.fromString("00000000-0000-0000-0000-000000000002")).join();

            assertEquals("Bearer minecraft-secret", registrationAuth.get());
            assertEquals("Bearer instance-secret", renewalAuth.get());
            assertEquals("Bearer instance-renewed", friendsAuth.get());
            assertEquals("minecraft-secret", friendsMinecraftAuth.get());
            assertEquals("minecraft-secret", requestMinecraftAuth.get());
            assertEquals("minecraft-secret", removeMinecraftAuth.get());
            assertEquals("presence-client", identity.presenceId());
            assertEquals(1, snapshot.friends().size());
            assertEquals(2, snapshot.friends().getFirst().presences().size());
            assertEquals(LinkPresenceStatus.HOSTING, snapshot.friends().getFirst().presences().getFirst().status());
            assertEquals(LinkFriendRelationship.INCOMING, snapshot.incoming().getFirst().relationship());
            assertEquals(LinkFriendRelationship.OUTGOING, addOutcome.relationship());
            assertEquals(LinkOfficialSyncStatus.SUCCESS, addOutcome.officialSync());
            assertEquals(LinkOfficialSyncStatus.SUCCESS, acceptOutcome.officialSync());
            assertEquals("/v1/friends/requests/" + incomingId, acceptedRequestPath.get());
            assertEquals("/v1/friends/requests/" + incomingId, deletedRequestPath.get());

            service.runtime().close(runtimeKey).join();
            assertTrue(closed.get());
            assertEquals("Bearer instance-renewed", closeAuth.get());
        } finally {
            service.shutdown().join();
            LinkContextHooks.remove(runtimeKey);
            server.stop(0);
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, status == 204 ? -1L : bytes.length);
        if (status != 204) exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void respondText(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static final class TestAccount implements MinecraftAccount {
        @Override
        public String getMcToken() { return "minecraft-secret"; }
        @Override
        public String getMcProfileId() { return "00000000-0000-0000-0000-000000000001"; }
        @Override
        public String getMcProfileName() { return "Tester"; }
    }
}
