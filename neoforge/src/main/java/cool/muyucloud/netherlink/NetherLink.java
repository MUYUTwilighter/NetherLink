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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import cool.muyucloud.netherlink.teacon.Bootstrap;
import cool.muyucloud.netherlink.teacon.CommonReg;
import cool.muyucloud.netherlink.teacon.ModBlocks;
import cool.muyucloud.netherlink.teacon.ModItems;
import cool.muyucloud.netherlink.teacon.entity.DoubleSidedSignBlockEntity;
import cool.muyucloud.netherlink.teacon.network.IntroCardActionPayload;

@Mod(NliConstants.MOD_ID)
public class NetherLink {
    public NetherLink(IEventBus eventBus) {
        NliSetup.init();
        NeoForge.EVENT_BUS.register(this);
        eventBus.addListener(this::onRegister);
        eventBus.addListener(this::onRegisterRenderers);
        eventBus.addListener(this::onRegisterPayloads);
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
            @SuppressWarnings("unchecked")
            var signType = (BlockEntityType<SignBlockEntity>) (Object) new BlockEntityType<>(
                DoubleSidedSignBlockEntity::new,
                new java.util.HashSet<>(ModBlocks.getAllIntroSignBlocks()));
            event.register(Registries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(NliConstants.MOD_ID, "intro_card_sign"), () -> signType);
            CommonReg.SIGN_BLOCK_ENTITY = () -> signType;

            @SuppressWarnings("unchecked")
            var hangingSignType = (BlockEntityType<SignBlockEntity>) (Object) new BlockEntityType<>(
                DoubleSidedSignBlockEntity::new,
                ModBlocks.getAllIntroHangingSignBlocks());
            event.register(Registries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(NliConstants.MOD_ID, "intro_card_hanging_sign"), () -> hangingSignType);
            CommonReg.HANGING_SIGN_BLOCK_ENTITY = () -> hangingSignType;
        }
        if (Registries.CREATIVE_MODE_TAB.equals(event.getRegistryKey())) {
            event.register(Registries.CREATIVE_MODE_TAB,
                Identifier.fromNamespaceAndPath(NliConstants.MOD_ID, "teacon"),
                () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable("itemGroup." + NliConstants.MOD_ID + ".teacon"))
                    .icon(() -> new ItemStack(ModItems.INTRO_CARD))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.INTRO_CARD);
                        output.accept(ModItems.FRIEND_CARD);
                        ModItems.INTRO_CARD_SIGN_ITEMS.values().forEach(output::accept);
                        ModItems.INTRO_CARD_HANGING_SIGN_ITEMS.values().forEach(output::accept);
                    })
                    .build());
        }
    }

    @SuppressWarnings("unused")
    public void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
            .playToServer(IntroCardActionPayload.TYPE, IntroCardActionPayload.STREAM_CODEC, this::handleIntroCardAction);
    }

    private void handleIntroCardAction(IntroCardActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            var level = player.level();
            var ds = level.getBlockEntity(payload.pos()) instanceof DoubleSidedSignBlockEntity d ? d : null;
            if (ds == null) return;

            if ("clear".equals(payload.action())) {
                ds.setText(new SignText(), true);
                ds.setEditor(null);
                var card = new ItemStack(ModItems.INTRO_CARD);
                if (!player.getInventory().add(card)) player.drop(card, false);
                level.playSound(null, payload.pos(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT,
                    SoundSource.BLOCKS, 1.0F, 1.0F);
            } else if ("edit".equals(payload.action())) {
                ds.setAllowedPlayerEditor(player.getUUID());
                player.openTextEdit(ds, true);
            }
        });
    }

    @SuppressWarnings("unused")
    public void onRegisterRenderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
            CommonReg.SIGN_BLOCK_ENTITY.get(),
            context -> new net.minecraft.client.renderer.blockentity.StandingSignRenderer(context));
        event.registerBlockEntityRenderer(
            CommonReg.HANGING_SIGN_BLOCK_ENTITY.get(),
            context -> new net.minecraft.client.renderer.blockentity.HangingSignRenderer(context));
    }
}
