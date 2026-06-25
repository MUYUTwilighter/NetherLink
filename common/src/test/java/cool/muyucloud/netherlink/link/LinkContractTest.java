package cool.muyucloud.netherlink.link;

import cool.muyucloud.netherlink.account.MinecraftAccount;
import cool.muyucloud.netherlink.link.exception.LinkException;
import cool.muyucloud.netherlink.link.exception.LinkFailures;
import cool.muyucloud.netherlink.link.hook.LinkContextHooks;
import cool.muyucloud.netherlink.link.hook.LinkRuntimeContext;
import cool.muyucloud.netherlink.link.model.*;
import cool.muyucloud.netherlink.link.official.OfficialRuntimeService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class LinkContractTest {
    @Test
    void runtimeContextsKeepAccountsAndCapabilitiesIsolated() {
        String firstKey = "test:first:" + UUID.randomUUID();
        String secondKey = "test:second:" + UUID.randomUUID();
        TestAccount first = TestAccount.create("First");
        TestAccount second = TestAccount.create("Second");
        try {
            LinkContextHooks.setServerConnection(firstKey, first, "First server", (_, _) -> {});
            LinkContextHooks.setServerConnection(secondKey, second, "Second server", (_, _) -> {});

            assertSame(first, LinkContextHooks.require(firstKey).account());
            assertSame(second, LinkContextHooks.require(secondKey).account());
            assertNotNull(LinkContextHooks.require(firstKey).serverConnection());
            assertNull(LinkContextHooks.require(firstKey).clientConnection());

            LinkContextHooks.removeServerConnection(firstKey);
            assertNull(LinkContextHooks.get(firstKey));
            assertSame(second, LinkContextHooks.require(secondKey).account());
        } finally {
            LinkContextHooks.remove(firstKey);
            LinkContextHooks.remove(secondKey);
        }
    }

    @Test
    void staleRegistrationDoesNotRemoveReplacementContext() {
        String key = "test:registration:" + UUID.randomUUID();
        TestAccount first = TestAccount.create("First");
        TestAccount second = TestAccount.create("Second");
        LinkContextHooks.Registration registration = LinkContextHooks.register(
            key,
            new LinkRuntimeContext(first, "First", null, (_, _) -> {}, null)
        );
        try (registration) {
            LinkContextHooks.register(key, new LinkRuntimeContext(second, "Second", null, (_, _) -> {}, null));
            registration.close();
            assertSame(second, LinkContextHooks.require(key).account());
        } finally {
            LinkContextHooks.remove(key);
        }
    }

    @Test
    void officialRuntimesAreIdempotentAndSeparatedByKey() {
        String firstKey = "test:runtime:first:" + UUID.randomUUID();
        String secondKey = "test:runtime:second:" + UUID.randomUUID();
        TestAccount first = TestAccount.create("First");
        TestAccount second = TestAccount.create("Second");
        OfficialRuntimeService runtimes = new OfficialRuntimeService();
        try {
            LinkContextHooks.setServerConnection(firstKey, first, "First", (_, _) -> {});
            LinkContextHooks.setServerConnection(secondKey, second, "Second", (_, _) -> {});

            var firstIdentity = runtimes.open(firstKey).join();
            assertEquals(firstIdentity, runtimes.open(firstKey).join());
            assertEquals(first.profileId, firstIdentity.profileId().toString());
            assertEquals(second.profileId, runtimes.open(secondKey).join().profileId().toString());
            assertNull(firstIdentity.presenceId());
            LinkRuntimeIdentity secondIdentity = Objects.requireNonNull(runtimes.current(secondKey));
            assertNull(secondIdentity.presenceId());
        } finally {
            runtimes.closeAll().join();
            LinkContextHooks.remove(firstKey);
            LinkContextHooks.remove(secondKey);
        }
    }

    @Test
    void failuresAreNormalizedAcrossAsyncBoundaries() {
        LinkFailure typed = new LinkFailure(LinkFailureCode.TARGET_UNAVAILABLE, "offline", true);
        assertEquals(typed, LinkFailures.from(new CompletionException(new LinkException(typed))));
        assertEquals(LinkFailureCode.CANCELLED, LinkFailures.from(new CancellationException("cancelled")).code());
        assertEquals(LinkFailureCode.TIMEOUT, LinkFailures.from(new TimeoutException("timeout")).code());
        assertEquals(LinkFailureCode.NETWORK, LinkFailures.from(new IOException("network")).code());
    }

    @Test
    void publicModelsDefensivelyCopyCollectionsAndValidateRoutes() {
        List<LinkFriendEntry> mutable = new ArrayList<>();
        mutable.add(new LinkFriendEntry(UUID.randomUUID(), null, LinkFriendRelationship.FRIEND, List.of()));
        LinkFriendSnapshot snapshot = new LinkFriendSnapshot(mutable, List.of(), List.of());

        mutable.clear();
        assertEquals(1, snapshot.friends().size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.friends().clear());
        assertThrows(IllegalArgumentException.class, () -> new LinkJoinTarget(UUID.randomUUID(), " "));
    }

    private record TestAccount(String profileId, String name) implements MinecraftAccount {

        private static TestAccount create(String name) {
                return new TestAccount(UUID.randomUUID().toString(), name);
            }

            @Override
            public String getMcToken() {
                return "test-token-" + this.profileId;
            }

            @Override
            public String getMcProfileId() {
                return this.profileId;
            }

            @Override
            public String getMcProfileName() {
                return this.name;
            }
        }
}
