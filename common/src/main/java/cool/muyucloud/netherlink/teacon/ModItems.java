package cool.muyucloud.netherlink.teacon;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.properties.WoodType;

import java.util.*;

/** Static holder for Teacon item instances. */
public final class ModItems {
    /** Standing+wall sign items keyed by wood type. */
    public static final Map<WoodType, Item> INTRO_CARD_SIGN_ITEMS = new LinkedHashMap<>();
    /** Hanging sign items keyed by wood type. */
    public static final Map<WoodType, Item> INTRO_CARD_HANGING_SIGN_ITEMS = new LinkedHashMap<>();

    public static Item TEACON_SIGN;
    public static Item INTRO_CARD;

    private ModItems() {}
}
