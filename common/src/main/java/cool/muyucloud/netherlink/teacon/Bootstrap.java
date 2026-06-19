package cool.muyucloud.netherlink.teacon;

import cool.muyucloud.netherlink.NliConstants;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import cool.muyucloud.netherlink.teacon.block.TeaconStandingSignBlock;
import cool.muyucloud.netherlink.teacon.block.TeaconWallSignBlock;
import cool.muyucloud.netherlink.teacon.item.IntroCardItem;
import cool.muyucloud.netherlink.teacon.block.IntroCardStandingSignBlock;
import cool.muyucloud.netherlink.teacon.block.IntroCardWallSignBlock;
import cool.muyucloud.netherlink.teacon.block.IntroCardCeilingHangingSignBlock;
import cool.muyucloud.netherlink.teacon.block.IntroCardWallHangingSignBlock;
import cool.muyucloud.netherlink.teacon.item.IntroCardHangingSignItem;
import cool.muyucloud.netherlink.teacon.item.IntroCardSignItem;
import cool.muyucloud.netherlink.teacon.item.TeaconSignItem;

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

    private static void registerBlocks() {
        if (registeredBlocks) return;
        var signKey = blockKey("teacon_sign");
        var wallKey = blockKey("teacon_wall_sign");

        var signProps = BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD).sound(SoundType.WOOD).noCollision()
            .strength(1.0F).ignitedByLava().pushReaction(PushReaction.DESTROY)
            .setId(signKey);
        var wallProps = BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD).sound(SoundType.WOOD).noCollision()
            .strength(1.0F).ignitedByLava().pushReaction(PushReaction.DESTROY)
            .setId(wallKey);

        ModBlocks.TEACON_STANDING_SIGN = Registry.register(
            BuiltInRegistries.BLOCK, signKey,
            new TeaconStandingSignBlock(WoodType.OAK, signProps));
        ModBlocks.TEACON_WALL_SIGN = Registry.register(
            BuiltInRegistries.BLOCK, wallKey,
            new TeaconWallSignBlock(WoodType.OAK, wallProps));

        var standingSignKey = blockKey("intro_card_standing_sign");
        ModBlocks.INTRO_CARD_STANDING_SIGN = Registry.register(
            BuiltInRegistries.BLOCK, standingSignKey,
            new IntroCardStandingSignBlock(WoodType.BAMBOO,
                BlockBehaviour.Properties.of().noCollision().strength(1.0F)
                    .sound(net.minecraft.world.level.block.SoundType.BAMBOO_WOOD).setId(standingSignKey)));

        var wallHangingSignKey = blockKey("intro_card_wall_hanging_sign");
        ModBlocks.INTRO_CARD_WALL_HANGING_SIGN = Registry.register(
            BuiltInRegistries.BLOCK, wallHangingSignKey,
            new IntroCardWallHangingSignBlock(WoodType.BAMBOO,
                BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .forceSolidOn().noCollision().strength(1.0F).ignitedByLava()
                    .sound(net.minecraft.world.level.block.SoundType.HANGING_SIGN).setId(wallHangingSignKey)));

        var ceilingHangingSignKey = blockKey("intro_card_ceiling_hanging_sign");
        ModBlocks.INTRO_CARD_CEILING_HANGING_SIGN = Registry.register(
            BuiltInRegistries.BLOCK, ceilingHangingSignKey,
            new IntroCardCeilingHangingSignBlock(WoodType.BAMBOO,
                BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .forceSolidOn().noCollision().strength(1.0F).ignitedByLava()
                    .sound(net.minecraft.world.level.block.SoundType.HANGING_SIGN).setId(ceilingHangingSignKey)));

        var introwallSignKey = blockKey("intro_card_wall_sign");
        ModBlocks.INTRO_CARD_WALL_SIGN = Registry.register(
            BuiltInRegistries.BLOCK, introwallSignKey,
            new IntroCardWallSignBlock(WoodType.BAMBOO,
                BlockBehaviour.Properties.of().noCollision().strength(1.0F)
                    .sound(net.minecraft.world.level.block.SoundType.BAMBOO_WOOD).setId(introwallSignKey)));

        registeredBlocks = true;
    }

    // ---- Items ----

    private static void registerItems() {
        if (registeredItems) return;
        var itemKey = ResourceKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(NliConstants.MOD_ID, "teacon_sign"));
        ModItems.TEACON_SIGN = Registry.register(
            BuiltInRegistries.ITEM, itemKey,
            new TeaconSignItem(ModBlocks.TEACON_STANDING_SIGN, ModBlocks.TEACON_WALL_SIGN,
                new Item.Properties().setId(itemKey)));

        var cardKey = itemKey("intro_card");
        ModItems.INTRO_CARD = Registry.register(
            BuiltInRegistries.ITEM, cardKey,
            new IntroCardItem(new Item.Properties().setId(cardKey)));

        ModItems.INTRO_CARD_STANDING_SIGN = Registry.register(
            BuiltInRegistries.ITEM, itemKey("intro_card_standing_sign"),
            new IntroCardSignItem(new Item.Properties().setId(itemKey("intro_card_standing_sign"))));
        ModItems.INTRO_CARD_HANGING_SIGN = Registry.register(
            BuiltInRegistries.ITEM, itemKey("intro_card_hanging_sign"),
            new IntroCardHangingSignItem(ModBlocks.INTRO_CARD_CEILING_HANGING_SIGN,
                ModBlocks.INTRO_CARD_WALL_HANGING_SIGN,
                new Item.Properties().setId(itemKey("intro_card_hanging_sign"))));

        registeredItems = true;
    }

    // ---- Helpers ----

    private static ResourceKey<Block> blockKey(String name) {
        return ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(NliConstants.MOD_ID, name));
    }

    private static ResourceKey<Item> itemKey(String name) {
        return ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(NliConstants.MOD_ID, name));
    }
}
