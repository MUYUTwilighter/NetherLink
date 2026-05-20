package cool.muyucloud.netherlink.mixin;

import cool.muyucloud.netherlink.access.Messenger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.PermissionSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.function.Supplier;

@Mixin(CommandSourceStack.class)
public abstract class CommandSourceStackMixin implements Messenger {
    @Shadow
    public abstract void sendSystemMessage(Component message);

    @Shadow
    public abstract PermissionSet permissions();

    @Override
    public void nli$sendMessage(Supplier<Component> msg) {
        this.sendSystemMessage(msg.get());
    }

    @Override
    public PermissionSet nli$permissions() {
        return this.permissions();
    }
}
