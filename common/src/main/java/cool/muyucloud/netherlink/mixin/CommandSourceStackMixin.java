package cool.muyucloud.netherlink.mixin;

import cool.muyucloud.netherlink.access.Messenger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.function.Supplier;

@Mixin(CommandSourceStack.class)
public abstract class CommandSourceStackMixin implements Messenger {
    @Invoker("sendSystemMessage")
    public abstract void nli$sendSystemMessage(Component message);

    @Override
    @Invoker("hasPermission")
    public abstract boolean nli$hasPermission(int permissionLevel);

    @Override
    public void nli$sendMessage(Supplier<Component> msg) {
        this.nli$sendSystemMessage(msg.get());
    }
}
