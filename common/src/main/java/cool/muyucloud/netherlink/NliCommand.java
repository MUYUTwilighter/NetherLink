package cool.muyucloud.netherlink;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import cool.muyucloud.netherlink.access.Messenger;
import cool.muyucloud.netherlink.account.AccountManager;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class NliCommand<S> {
    private static final AtomicInteger THREAD_ID = new AtomicInteger();
    private final ExecutorService executor = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "NetherLink Command-" + THREAD_ID.getAndIncrement());
        thread.setDaemon(true);
        return thread;
    });
    private final LiteralArgumentBuilder<S> rootFull = LiteralArgumentBuilder.literal("netherlink");
    private final LiteralArgumentBuilder<S> root = LiteralArgumentBuilder.literal("nli");

    public NliCommand() {
        LiteralArgumentBuilder<S> add = LiteralArgumentBuilder.literal("add");
        add.executes(context -> executeAsync(context.getSource(), "add account", AccountManager::add));

        LiteralArgumentBuilder<S> list = LiteralArgumentBuilder.literal("list");
        list.executes(context -> executeAsync(context.getSource(), "list accounts", AccountManager::list));

        LiteralArgumentBuilder<S> refresh = LiteralArgumentBuilder.literal("refresh");
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

        LiteralArgumentBuilder<S> remove = LiteralArgumentBuilder.literal("remove");
        remove.then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<S, String>argument("name", StringArgumentType.word())
            .suggests((context, builder) -> SharedSuggestionProvider.suggest(AccountManager.names(), builder))
            .executes(context -> {
                String name = StringArgumentType.getString(context, "name");
                return executeAsync(context.getSource(), "remove account " + name, messenger -> {
                    if (AccountManager.remove(name)) {
                        messenger.nli$sendMessage(() -> new net.minecraft.network.chat.TextComponent("NetherLink account \"" + name + "\" removed."));
                    } else {
                        messenger.nli$sendMessage(() -> new net.minecraft.network.chat.TextComponent("NetherLink account \"" + name + "\" was not found."));
                    }
                });
            }));

        LiteralArgumentBuilder<S> toggle = LiteralArgumentBuilder.literal("toggle");
        toggle.then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<S, String>argument("name", StringArgumentType.word())
            .suggests((context, builder) -> SharedSuggestionProvider.suggest(AccountManager.names(), builder))
            .executes(context -> {
                String name = StringArgumentType.getString(context, "name");
                return executeAsync(context.getSource(), "toggle account " + name, messenger -> AccountManager.toggle(name, messenger));
            }));

        LiteralArgumentBuilder<S> publish = LiteralArgumentBuilder.literal("publish");
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

        LiteralArgumentBuilder<S> revoke = LiteralArgumentBuilder.literal("revoke");
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
            return m.nli$hasPermission(Commands.LEVEL_ADMINS);
        });
    }

    public void register(CommandDispatcher<S> dispatcher) {
        CommandNode<S> node = dispatcher.register(root);
        rootFull.redirect(node);
        dispatcher.register(rootFull);
    }

    private int executeAsync(S source, String taskName, Consumer<Messenger> action) {
        Messenger messenger = Messenger.of(source);
        messenger.nli$sendMessage(() -> new net.minecraft.network.chat.TextComponent("NetherLink task started: " + taskName));
        CompletableFuture.runAsync(() -> action.accept(messenger), executor)
            .thenRun(() -> messenger.nli$sendMessage(() -> new net.minecraft.network.chat.TextComponent("NetherLink task completed: " + taskName)))
            .exceptionally(error -> {
                Throwable cause = error.getCause() == null ? error : error.getCause();
                NliConstants.LOG.warn("NetherLink task failed: {}", taskName, cause);
                messenger.nli$sendMessage(() -> new net.minecraft.network.chat.TextComponent("NetherLink task failed: " + cause.getMessage()));
                return null;
            });
        return 1;
    }
}
