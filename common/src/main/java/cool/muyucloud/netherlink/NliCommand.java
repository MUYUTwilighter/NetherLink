package cool.muyucloud.netherlink;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import cool.muyucloud.netherlink.access.Messenger;
import cool.muyucloud.netherlink.account.AccountManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class NliCommand<S> {
    private final ExecutorService executor = Executors.newThreadPerTaskExecutor(
        Thread.ofVirtual().name("NetherLink Command-", 0).factory()
    );
    private final LiteralArgumentBuilder<S> rootFull = LiteralArgumentBuilder.literal("netherlink");
    private final LiteralArgumentBuilder<S> root = LiteralArgumentBuilder.literal("nli");
    private final LiteralArgumentBuilder<S> add = LiteralArgumentBuilder.literal("add");
    private final LiteralArgumentBuilder<S> refresh = LiteralArgumentBuilder.literal("refresh");
    private final LiteralArgumentBuilder<S> remove = LiteralArgumentBuilder.literal("remove");
    private final LiteralArgumentBuilder<S> toggle = LiteralArgumentBuilder.literal("toggle");

    public NliCommand() {
        add.executes(context -> {
            return executeAsync(context.getSource(), "add account", AccountManager::add);
        });
        root.then(add);
        root.requires(source -> {
            Messenger m = Messenger.of(source);
            return m.cif$permissions().hasPermission(Permissions.COMMANDS_ADMIN);
        });
    }

    public void register(CommandDispatcher<S> dispatcher) {
        CommandNode<S> node = dispatcher.register(root);
        rootFull.redirect(node);
        dispatcher.register(rootFull);
    }

    private int executeAsync(S source, String taskName, Consumer<Messenger> action) {
        Messenger messenger = Messenger.of(source);
        messenger.cif$sendMessage(() -> Component.literal("NetherLink task started: " + taskName));
        CompletableFuture.runAsync(() -> action.accept(messenger), executor)
            .thenRun(() -> messenger.cif$sendMessage(() -> Component.literal("NetherLink task completed: " + taskName)))
            .exceptionally(error -> {
                Throwable cause = error.getCause() == null ? error : error.getCause();
                NliConstants.LOG.warn("NetherLink task failed: {}", taskName, cause);
                messenger.cif$sendMessage(() -> Component.literal("NetherLink task failed: " + cause.getMessage()));
                return null;
            });
        return 1;
    }
}
