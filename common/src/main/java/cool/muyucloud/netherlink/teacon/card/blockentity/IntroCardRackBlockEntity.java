package cool.muyucloud.netherlink.teacon.card.blockentity;

import cool.muyucloud.netherlink.teacon.CommonReg;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * BlockEntity for {@link IntroCardRackBlock}.
 * Stores interaction count as placeholder �� not NBT-persistent yet.
 * Extend with saveAdditional/loadAdditional for real storage.
 */
public class IntroCardRackBlockEntity extends BlockEntity {

    private int counter;

    public IntroCardRackBlockEntity(BlockPos pos, BlockState state) {
        super(CommonReg.RACK_BLOCK_ENTITY.get(), pos, state);
    }

    public int getCounter() { return counter; }

    public void incrementCounter() {
        counter++;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}