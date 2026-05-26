package cool.muyucloud.netherlink.p2p;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class RtcMinecraftConnection extends Connection {
    private final @Nullable UUID intendedProfileId;

    public RtcMinecraftConnection(PacketFlow receiving) {
        this(receiving, null);
    }

    RtcMinecraftConnection(PacketFlow receiving, @Nullable UUID intendedProfileId) {
        super(receiving);
        this.intendedProfileId = intendedProfileId;
    }

    @Override
    public boolean isMemoryConnection() {
        return true;
    }

    @Nullable
    UUID intendedProfileId() {
        return this.intendedProfileId;
    }
}
