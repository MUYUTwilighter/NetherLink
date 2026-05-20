package cool.muyucloud.netherlink.access;

import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.PermissionSet;

import java.util.function.Supplier;

public interface Messenger {
    static <S> Messenger of(S source) {
        if (source instanceof Messenger m) {
            return m;
        } else {
            throw new IllegalStateException("Duck type is not affective on %s".formatted(source.getClass().getPackageName()));
        }
    }

    void nli$sendMessage(Supplier<Component> msg);

    PermissionSet nli$permissions();
}
