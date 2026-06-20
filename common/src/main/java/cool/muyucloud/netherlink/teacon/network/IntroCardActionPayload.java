package cool.muyucloud.netherlink.teacon.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import static cool.muyucloud.netherlink.NliConstants.MOD_ID;

/** Client→Server packet for IntroCard owner screen actions. */
public record IntroCardActionPayload(String action, BlockPos pos) implements CustomPacketPayload {

    public static final Type<IntroCardActionPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(MOD_ID, "intro_card_action"));

    public static final StreamCodec<FriendlyByteBuf, IntroCardActionPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, IntroCardActionPayload::action,
            BlockPos.STREAM_CODEC,      IntroCardActionPayload::pos,
            IntroCardActionPayload::new);

    @Override
    public Type<IntroCardActionPayload> type() { return TYPE; }
}
