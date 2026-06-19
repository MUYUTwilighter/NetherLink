package cool.muyucloud.netherlink.link.nli;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import cool.muyucloud.netherlink.account.MinecraftAccount;
import cool.muyucloud.netherlink.link.hook.LinkContextHooks;
import cool.muyucloud.netherlink.link.model.LinkFriendRelationship;
import cool.muyucloud.netherlink.link.model.LinkPresenceStatus;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NliLinkServiceContractTest {
    @Test
    void registersRuntimeUsesInstanceTokenAndParsesMultiPresenceFriends() throws Exception {
        AtomicReference<String> registrationAuth = new AtomicReference<>();
        AtomicReference<String> renewalAuth = new AtomicReference<>();
        AtomicReference<String> friendsAuth = new AtomicReference<>();
        AtomicReference<String> acceptedRequestPath = new AtomicReference<>();
        AtomicReference<String> deletedRequestPath = new AtomicReference<>();
        AtomicReference<String> closeAuth = new AtomicReference<>();
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
        server.createContext("/v1/friends", exchange -> {
            friendsAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, """
                {
                  "friends":[{
                    "profileId":"00000000-0000-0000-0000-000000000002",
                    "name":"Friend",
                    "source":"netherlink",
                    "presences":[
                      {"profileId":"00000000-0000-0000-0000-000000000002","presenceId":"friend-a","status":"HOSTING","joinable":true,"sessionId":null,"endpoint":null,"displayText":"World A","updatedAt":"2026-06-19T00:00:00Z","expiresAt":"2099-01-01T00:00:00Z"},
                      {"profileId":"00000000-0000-0000-0000-000000000002","presenceId":"friend-b","status":"ONLINE","joinable":false,"sessionId":null,"endpoint":null,"displayText":"Instance B","updatedAt":"2026-06-19T00:00:00Z","expiresAt":"2099-01-01T00:00:00Z"}
                    ]
                  }],
                  "incomingRequests":[{"profileId":"00000000-0000-0000-0000-000000000003","name":"Incoming","source":"netherlink"}],
                  "outgoingRequests":[]
                }
                """);
        });
        server.createContext("/v1/friends/requests", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("POST".equals(exchange.getRequestMethod())) {
                acceptedRequestPath.set(path);
                respond(exchange, 200, """
                    {"result":"SUCCESS","relationship":"ACCEPTED","officialSync":"SKIPPED"}
                    """);
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
            LinkContextHooks.setClientConnection(runtimeKey, new TestAccount(), "Test client", _ -> {});
            var identity = service.runtime().open(runtimeKey).join();
            service.runtime().renew(runtimeKey).join();
            var friends = service.createFriendService(runtimeKey);
            var snapshot = friends.refresh().join();
            UUID incomingId = UUID.fromString("00000000-0000-0000-0000-000000000003");
            friends.accept(incomingId).join();
            friends.decline(incomingId).join();

            assertEquals("Bearer minecraft-secret", registrationAuth.get());
            assertEquals("Bearer instance-secret", renewalAuth.get());
            assertEquals("Bearer instance-renewed", friendsAuth.get());
            assertEquals("presence-client", identity.presenceId());
            assertEquals(1, snapshot.friends().size());
            assertEquals(2, snapshot.friends().getFirst().presences().size());
            assertEquals(LinkPresenceStatus.HOSTING, snapshot.friends().getFirst().presences().getFirst().status());
            assertEquals(LinkFriendRelationship.INCOMING, snapshot.incoming().getFirst().relationship());
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

    private static final class TestAccount implements MinecraftAccount {
        @Override
        public String getMcToken() { return "minecraft-secret"; }
        @Override
        public String getMcProfileId() { return "00000000-0000-0000-0000-000000000001"; }
        @Override
        public String getMcProfileName() { return "Tester"; }
    }
}
