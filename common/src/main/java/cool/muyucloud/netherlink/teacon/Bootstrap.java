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
import cool.muyucloud.netherlink.teacon.card.item.IntroCardItem;
import cool.muyucloud.netherlink.teacon.card.block.StandingIntroCardRackBlock;
import cool.muyucloud.netherlink.teacon.card.block.WallIntroCardRackBlock;
import cool.muyucloud.netherlink.teacon.card.block.HangingIntroCardRackBlock;
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

        // Rack blocks
        var standingRackKey = blockKey("standing_intro_card_rack");
        ModBlocks.STANDING_INTRO_CARD_RACK = Registry.register(
            BuiltInRegistries.BLOCK, standingRackKey,
            new StandingIntroCardRackBlock(
                StandingIntroCardRackBlock.props().setId(standingRackKey)));

        var wallRackKey = blockKey("wall_intro_card_rack");
        ModBlocks.WALL_INTRO_CARD_RACK = Registry.register(
            BuiltInRegistries.BLOCK, wallRackKey,
            new WallIntroCardRackBlock(
                WallIntroCardRackBlock.props().setId(wallRackKey)));

        var hangingRackKey = blockKey("hanging_intro_card_rack");
        ModBlocks.HANGING_INTRO_CARD_RACK = Registry.register(
            BuiltInRegistries.BLOCK, hangingRackKey,
            new HangingIntroCardRackBlock(
                HangingIntroCardRackBlock.props().setId(hangingRackKey)));

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
