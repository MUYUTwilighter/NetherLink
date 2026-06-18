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
        registeredItems = true;
    }

    // ---- Helpers ----

    private static ResourceKey<Block> blockKey(String name) {
        return ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(NliConstants.MOD_ID, name));
    }
}
