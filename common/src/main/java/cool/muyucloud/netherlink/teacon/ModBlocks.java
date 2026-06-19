package cool.muyucloud.netherlink.teacon;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.WoodType;

import java.util.*;

/** Static holder for Teacon block instances. */
public final class ModBlocks {
    /** Standing sign blocks keyed by wood type. */
    public static final Map<WoodType, Block> INTRO_CARD_STANDING_SIGNS = new LinkedHashMap<>();
    /** Wall sign blocks keyed by wood type. */
    public static final Map<WoodType, Block> INTRO_CARD_WALL_SIGNS = new LinkedHashMap<>();
    /** Wall hanging sign blocks keyed by wood type. */
    public static final Map<WoodType, Block> INTRO_CARD_WALL_HANGING_SIGNS = new LinkedHashMap<>();
    /** Ceiling hanging sign blocks keyed by wood type. */
    public static final Map<WoodType, Block> INTRO_CARD_CEILING_HANGING_SIGNS = new LinkedHashMap<>();

    private ModBlocks() {}

    /** @return all standing + wall IntroCard sign blocks (for SIGN_BLOCK_ENTITY valid set). */
    public static Set<Block> getAllIntroSignBlocks() {
        var set = new HashSet<Block>();
        set.addAll(INTRO_CARD_STANDING_SIGNS.values());
        set.addAll(INTRO_CARD_WALL_SIGNS.values());
        return Collections.unmodifiableSet(set);
    }

    /** @return all wall_hanging + ceiling_hanging IntroCard sign blocks (for HANGING_SIGN_BLOCK_ENTITY valid set). */
    public static Set<Block> getAllIntroHangingSignBlocks() {
        var set = new HashSet<Block>();
        set.addAll(INTRO_CARD_WALL_HANGING_SIGNS.values());
        set.addAll(INTRO_CARD_CEILING_HANGING_SIGNS.values());
        return Collections.unmodifiableSet(set);
    }
}
