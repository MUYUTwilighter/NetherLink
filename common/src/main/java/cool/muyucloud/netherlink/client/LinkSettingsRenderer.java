package cool.muyucloud.netherlink.client;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.navigation.ScreenRectangle;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

interface LinkSettingsRenderer {
    void visitChildren(Consumer<AbstractWidget> consumer);

    void doLayout(ScreenRectangle area);

    default void load() {
    }

    default boolean hasChanges() {
        return false;
    }

    default boolean canApply() {
        return false;
    }

    default CompletableFuture<Boolean> apply() {
        return CompletableFuture.completedFuture(false);
    }
}
