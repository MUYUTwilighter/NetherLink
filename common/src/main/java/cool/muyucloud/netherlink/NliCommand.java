package cool.muyucloud.netherlink;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import cool.muyucloud.netherlink.access.Messenger;
import cool.muyucloud.netherlink.account.AccountManager;
import net.minecraft.commands.SharedSuggestionProvider;
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
    private final LiteralArgumentBuilder<S> list = LiteralArgumentBuilder.literal("list");
    private final LiteralArgumentBuilder<S> publish = LiteralArgumentBuilder.literal("publish");
    private final LiteralArgumentBuilder<S> revoke = LiteralArgumentBuilder.literal("revoke");

    public NliCommand() {
        add.executes(context -> executeAsync(context.getSource(), "add account", AccountManager::add));

        list.executes(context -> executeAsync(context.getSource(), "list accounts", AccountManager::list));

        refresh
            .executes(context -> executeAsync(context.getSource(), "refresh all accounts", messenger -> AccountManager.refresh(true, messenger)))
            .then(LiteralArgumentBuilder.<S>literal("all")
                .executes(context -> executeAsync(context.getSource(), "refresh all accounts", messenger -> AccountManager.refresh(true, messenger))))
            .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<S, String>argument("name", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(AccountManager.names(), builder))
                .executes(context -> {
                    String name = StringArgumentType.getString(context, "name");
                    return executeAsync(context.getSource(), "refresh account " + name, messenger -> AccountManager.refresh(name, true, messenger));
                }));

        remove.then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<S, String>argument("name", StringArgumentType.word())
            .suggests((context, builder) -> SharedSuggestionProvider.suggest(AccountManager.names(), builder))
            .executes(context -> {
                String name = StringArgumentType.getString(context, "name");
                return executeAsync(context.getSource(), "remove account " + name, messenger -> {
                    if (AccountManager.remove(name)) {
                        messenger.cif$sendMessage(() -> Component.literal("NetherLink account \"" + name + "\" removed."));
                    } else {
                        messenger.cif$sendMessage(() -> Component.literal("NetherLink account \"" + name + "\" was not found."));
                    }
                });
            }));

        toggle.then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<S, String>argument("name", StringArgumentType.word())
            .suggests((context, builder) -> SharedSuggestionProvider.suggest(AccountManager.names(), builder))
            .executes(context -> {
                String name = StringArgumentType.getString(context, "name");
                return executeAsync(context.getSource(), "toggle account " + name, messenger -> AccountManager.toggle(name, messenger));
            }));

        publish
            .executes(context -> executeAsync(context.getSource(), "publish all accounts", AccountManager::publish))
            .then(LiteralArgumentBuilder.<S>literal("all")
                .executes(context -> executeAsync(context.getSource(), "publish all accounts", AccountManager::publish)))
            .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<S, String>argument("name", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(AccountManager.names(), builder))
                .executes(context -> {
                    String name = StringArgumentType.getString(context, "name");
                    return executeAsync(context.getSource(), "publish account " + name, messenger -> AccountManager.publish(name, messenger));
                }));

        revoke
            .executes(context -> executeAsync(context.getSource(), "revoke all accounts", AccountManager::revoke))
            .then(LiteralArgumentBuilder.<S>literal("all")
                .executes(context -> executeAsync(context.getSource(), "revoke all accounts", AccountManager::revoke)))
            .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<S, String>argument("name", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(AccountManager.names(), builder))
                .executes(context -> {
                    String name = StringArgumentType.getString(context, "name");
                    return executeAsync(context.getSource(), "revoke account " + name, messenger -> AccountManager.revoke(name, messenger));
                }));

        root.then(add).then(list).then(refresh).then(remove).then(toggle).then(publish).then(revoke);
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
