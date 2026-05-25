package cool.muyucloud.netherlink.mixin;

import cool.muyucloud.netherlink.access.Messenger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.function.Supplier;

@Mixin(CommandSourceStack.class)
public abstract class CommandSourceStackMixin implements Messenger {
    @Shadow
    public abstract void sendSuccess(Component message, boolean allowLogging);

    @Shadow
    public abstract boolean hasPermission(int level);

    @Override
    public void nli$sendMessage(Supplier<Component> msg) {
        this.sendSuccess(msg.get(), false);
    }

    @Override
    public boolean nli$hasPermission(int level) {
        return this.hasPermission(level);
    }
}
