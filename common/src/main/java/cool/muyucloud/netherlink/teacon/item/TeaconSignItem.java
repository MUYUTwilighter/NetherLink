package cool.muyucloud.netherlink.teacon.item;

import net.minecraft.world.item.SignItem;
import net.minecraft.world.level.block.Block;

/** Teacon sign item — extends vanilla {@link SignItem}. */
public class TeaconSignItem extends SignItem {
    public TeaconSignItem(Block standingBlock, Block wallBlock, Properties properties) {
        super(standingBlock, wallBlock, properties);
    }
}
