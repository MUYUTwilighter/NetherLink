package cool.muyucloud.netherlink;


import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SignBlockEntity;

import cool.muyucloud.netherlink.teacon.Bootstrap;
import cool.muyucloud.netherlink.teacon.CommonReg;
import cool.muyucloud.netherlink.teacon.ModBlocks;
import cool.muyucloud.netherlink.teacon.ModItems;
import cool.muyucloud.netherlink.teacon.card.blockentity.IntroCardRackBlockEntity;

import java.util.Set;

@Mod(NliConstants.MOD_ID)
public class NetherLink {
    public NetherLink(IEventBus eventBus) {
        NliSetup.init();
        NeoForge.EVENT_BUS.register(this);
        eventBus.addListener(this::onRegister);
        eventBus.addListener(this::onRegisterRenderers);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        NliSetup.onServerStarted(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        NliSetup.onServerStopping(event.getServer());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        NliConstants.SERVER_COMMAND.register(event.getDispatcher());
    }

    public void onRegister(RegisterEvent event) {
        Bootstrap.initFor(event.getRegistryKey());
        if (Registries.BLOCK_ENTITY_TYPE.equals(event.getRegistryKey())) {
            var signType = new BlockEntityType<>(
                SignBlockEntity::new,
                Set.of(ModBlocks.TEACON_STANDING_SIGN, ModBlocks.TEACON_WALL_SIGN));
            event.register(Registries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(NliConstants.MOD_ID, "teacon_sign"), () -> signType);
            CommonReg.SIGN_BLOCK_ENTITY = () -> signType;

            var rackType = new BlockEntityType<>(
                IntroCardRackBlockEntity::new,
                Set.of(ModBlocks.STANDING_INTRO_CARD_RACK,
                       ModBlocks.WALL_INTRO_CARD_RACK,
                       ModBlocks.HANGING_INTRO_CARD_RACK));
            event.register(Registries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(NliConstants.MOD_ID, "intro_card_rack"), () -> rackType);
            CommonReg.RACK_BLOCK_ENTITY = () -> rackType;
        }
        if (Registries.CREATIVE_MODE_TAB.equals(event.getRegistryKey())) {
            event.register(Registries.CREATIVE_MODE_TAB,
                Identifier.fromNamespaceAndPath(NliConstants.MOD_ID, "teacon"),
                () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable("itemGroup." + NliConstants.MOD_ID + ".teacon"))
                    .icon(() -> new ItemStack(ModItems.TEACON_SIGN))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.TEACON_SIGN);
                        output.accept(ModItems.INTRO_CARD);
                        output.accept(ModBlocks.STANDING_INTRO_CARD_RACK);
                        output.accept(ModBlocks.WALL_INTRO_CARD_RACK);
                        output.accept(ModBlocks.HANGING_INTRO_CARD_RACK);
                    })
                    .build());
        }
    }

    @SuppressWarnings("unused")
    public void onRegisterRenderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
            CommonReg.SIGN_BLOCK_ENTITY.get(),
            context -> new net.minecraft.client.renderer.blockentity.StandingSignRenderer(context));
    }
}
