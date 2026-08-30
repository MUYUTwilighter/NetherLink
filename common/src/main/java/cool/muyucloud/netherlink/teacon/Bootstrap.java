package cool.muyucloud.netherlink.teacon;

import cool.muyucloud.netherlink.NliConstants;
import cool.muyucloud.netherlink.teacon.block.IntroCardCeilingHangingSignBlock;
import cool.muyucloud.netherlink.teacon.block.IntroCardStandingSignBlock;
import cool.muyucloud.netherlink.teacon.block.IntroCardWallHangingSignBlock;
import cool.muyucloud.netherlink.teacon.block.IntroCardWallSignBlock;
import cool.muyucloud.netherlink.teacon.item.FriendCardItem;
import cool.muyucloud.netherlink.teacon.item.IntroCardHangingSignItem;
import cool.muyucloud.netherlink.teacon.item.IntroCardItem;
import cool.muyucloud.netherlink.teacon.item.IntroCardSignItem;
import net.minecraft.IdentifierException;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.MapColor;

/**
 * Common registration — blocks, items, creative tab via vanilla {@link Registry#register}.
 * Called by Fabric (onInitialize) and NeoForge (RegisterEvent).
 *
 * <p>{@link CommonReg#SIGN_BLOCK_ENTITY} must be set by the platform
 * before block entities are created at runtime.</p>
 */
public final class Bootstrap {

    private static boolean registeredBlocks;
    private static boolean registeredItems;

    private Bootstrap() {}

    /** Fabric: call once during onInitialize (all registries open). */
    public static void init() {
        registerBlocks();
        registerItems();
    }

    /** NeoForge: called per-registry during RegisterEvent. */
    public static void initFor(ResourceKey<?> registryKey) {
        if (Registries.BLOCK.equals(registryKey)) registerBlocks();
        if (Registries.ITEM.equals(registryKey)) registerItems();
    }

    // ---- Blocks ----

    @SuppressWarnings("unchecked")
    private static void registerBlocks() {
        if (registeredBlocks) return;

        // Register IntroCard sign blocks for every vanilla wood type
        WoodType.values().forEach(wood -> {
            String name = wood.name().toLowerCase(java.util.Locale.ROOT);
            SoundType woodSound = woodSound(wood);

            if (!validateWoodName(name)) return;

            // Standing sign
            var standingKey = blockKey("intro_card_" + name + "_standing_sign");
            var standing = new IntroCardStandingSignBlock(wood,
                BlockBehaviour.Properties.of().noCollision().strength(1.0F)
                    .sound(woodSound).setId(standingKey));
            ModBlocks.INTRO_CARD_STANDING_SIGNS.put(wood, Registry.register(
                BuiltInRegistries.BLOCK, standingKey, standing));

            // Wall sign
            var wallSignKey = blockKey("intro_card_" + name + "_wall_sign");
            var wallSign = new IntroCardWallSignBlock(wood,
                BlockBehaviour.Properties.of().noCollision().strength(1.0F)
                    .sound(woodSound).setId(wallSignKey));
            ModBlocks.INTRO_CARD_WALL_SIGNS.put(wood, Registry.register(
                BuiltInRegistries.BLOCK, wallSignKey, wallSign));

            // Wall hanging sign
            var wallHangingKey = blockKey("intro_card_" + name + "_wall_hanging_sign");
            var wallHanging = new IntroCardWallHangingSignBlock(wood,
                BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .forceSolidOn().noCollision().strength(1.0F).ignitedByLava()
                    .sound(SoundType.HANGING_SIGN).setId(wallHangingKey));
            ModBlocks.INTRO_CARD_WALL_HANGING_SIGNS.put(wood, Registry.register(
                BuiltInRegistries.BLOCK, wallHangingKey, wallHanging));

            // Ceiling hanging sign
            var ceilingKey = blockKey("intro_card_" + name + "_ceiling_hanging_sign");
            var ceiling = new IntroCardCeilingHangingSignBlock(wood,
                BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .forceSolidOn().noCollision().strength(1.0F).ignitedByLava()
                    .sound(SoundType.HANGING_SIGN).setId(ceilingKey));
            ModBlocks.INTRO_CARD_CEILING_HANGING_SIGNS.put(wood, Registry.register(
                BuiltInRegistries.BLOCK, ceilingKey, ceiling));
        });

        registeredBlocks = true;
    }

    private static boolean validateWoodName(String name) {
        try {
            Identifier.fromNamespaceAndPath(NliConstants.MOD_ID, name);
            return true;
        } catch (IdentifierException e) {
            return false;
        }
    }

    private static void registerItems() {
        if (registeredItems) return;
        var cardKey = itemKey("intro_card");
        ModItems.INTRO_CARD = Registry.register(
            BuiltInRegistries.ITEM, cardKey,
            new IntroCardItem(new Item.Properties().setId(cardKey)));

        var friendCardKey = itemKey("friend_card");
        ModItems.FRIEND_CARD = Registry.register(
            BuiltInRegistries.ITEM, friendCardKey,
            new FriendCardItem(new Item.Properties().setId(friendCardKey)));

        // Register sign items for every wood type
        WoodType.values().forEach(wood -> {
            String name = wood.name().toLowerCase(java.util.Locale.ROOT);
            var standing = ModBlocks.INTRO_CARD_STANDING_SIGNS.get(wood);
            var wall = ModBlocks.INTRO_CARD_WALL_SIGNS.get(wood);
            var ceiling = ModBlocks.INTRO_CARD_CEILING_HANGING_SIGNS.get(wood);
            var wallHanging = ModBlocks.INTRO_CARD_WALL_HANGING_SIGNS.get(wood);

            // Standing+wall sign item
            ModItems.INTRO_CARD_SIGN_ITEMS.put(wood, Registry.register(
                BuiltInRegistries.ITEM, itemKey("intro_card_" + name + "_sign"),
                new IntroCardSignItem(standing, wall, new Item.Properties().setId(itemKey("intro_card_" + name + "_sign")))));

            // Hanging sign item
            ModItems.INTRO_CARD_HANGING_SIGN_ITEMS.put(wood, Registry.register(
                BuiltInRegistries.ITEM, itemKey("intro_card_" + name + "_hanging_sign"),
                new IntroCardHangingSignItem(ceiling, wallHanging,
                    new Item.Properties().setId(itemKey("intro_card_" + name + "_hanging_sign")))));
        });

        registeredItems = true;
    }

    /** Map wood type to the appropriate SoundType for standing/wall signs. */
    private static SoundType woodSound(WoodType wood) {
        String name = wood.name();
        if (name.equals("CHERRY")) return SoundType.CHERRY_WOOD;
        if (name.equals("BAMBOO")) return SoundType.BAMBOO_WOOD;
        if (name.equals("CRIMSON") || name.equals("WARPED")) return SoundType.NETHER_WOOD;
        return SoundType.WOOD;
    }

    private static ResourceKey<Block> blockKey(String name) {
        return ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(NliConstants.MOD_ID, name));
    }

    private static ResourceKey<Item> itemKey(String name) {
        return ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(NliConstants.MOD_ID, name));
    }
}
