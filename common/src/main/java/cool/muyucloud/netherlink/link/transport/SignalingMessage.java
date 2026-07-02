package cool.muyucloud.netherlink.link.transport;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.onvoid.webrtc.RTCIceCandidate;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Backend-neutral join-control and WebRTC negotiation message payload.
 * Concrete signaling adapters wrap this payload in their own routing envelope; source identity
 * must always come from authenticated transport state rather than caller-supplied fields.
 */
public sealed interface SignalingMessage permits SignalingMessage.FriendJoin, SignalingMessage.WebRtc {
    Codec<SignalingMessage> CODEC = Type.CODEC.dispatch(SignalingMessage::type, Type::codec);

    static SignalingMessage joinAccepted(String sessionId) {
        return new FriendJoin.Accepted(sessionId);
    }

    static SignalingMessage joinRejected(String sessionId) {
        return new FriendJoin.Rejected(sessionId);
    }

    static SignalingMessage answer(String sessionId, String sdp) {
        return new WebRtc.Answer(sessionId, sdp);
    }

    static SignalingMessage iceCandidate(String sessionId, RTCIceCandidate candidate) {
        return new WebRtc.IceCandidate(sessionId, candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex);
    }

    Type type();

    String sessionId();

    /** Join request lifecycle messages that precede or terminate SDP negotiation. */
    sealed interface FriendJoin extends SignalingMessage permits FriendJoin.Request, FriendJoin.Accepted, FriendJoin.Rejected, FriendJoin.InviteDeclined {
        record Request(String sessionId) implements FriendJoin {
            private static final MapCodec<Request> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("sessionId").forGetter(Request::sessionId)
            ).apply(instance, Request::new));

            @Override
            public Type type() {
                return Type.JOIN_REQUEST;
            }
        }

        record Accepted(String sessionId) implements FriendJoin {
            private static final MapCodec<Accepted> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("sessionId").forGetter(Accepted::sessionId)
            ).apply(instance, Accepted::new));

            @Override
            public Type type() {
                return Type.JOIN_ACCEPTED;
            }
        }

        record Rejected(String sessionId) implements FriendJoin {
            private static final MapCodec<Rejected> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("sessionId").forGetter(Rejected::sessionId)
            ).apply(instance, Rejected::new));

            @Override
            public Type type() {
                return Type.JOIN_REJECTED;
            }
        }

        record InviteDeclined(String sessionId) implements FriendJoin {
            private static final MapCodec<InviteDeclined> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("sessionId").forGetter(InviteDeclined::sessionId)
            ).apply(instance, InviteDeclined::new));

            @Override
            public Type type() {
                return Type.INVITE_DECLINED;
            }
        }
    }

    /** SDP and ICE messages exchanged after a join request is accepted. */
    sealed interface WebRtc extends SignalingMessage permits WebRtc.Offer, WebRtc.Answer, WebRtc.IceCandidate {
        record Offer(String sessionId, String sdp) implements WebRtc {
            private static final MapCodec<Offer> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("sessionId").forGetter(Offer::sessionId),
                Codec.STRING.fieldOf("sdp").forGetter(Offer::sdp)
            ).apply(instance, Offer::new));

            @Override
            public Type type() {
                return Type.OFFER;
            }
        }

        record Answer(String sessionId, String sdp) implements WebRtc {
            private static final MapCodec<Answer> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("sessionId").forGetter(Answer::sessionId),
                Codec.STRING.fieldOf("sdp").forGetter(Answer::sdp)
            ).apply(instance, Answer::new));

            @Override
            public Type type() {
                return Type.ANSWER;
            }
        }

        record IceCandidate(String sessionId, String candidate, String sdpMid, int sdpMLineIndex) implements WebRtc {
            private static final MapCodec<IceCandidate> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("sessionId").forGetter(IceCandidate::sessionId),
                Codec.STRING.fieldOf("candidate").forGetter(IceCandidate::candidate),
                Codec.STRING.fieldOf("sdpMid").forGetter(IceCandidate::sdpMid),
                Codec.INT.fieldOf("sdpMLineIndex").forGetter(IceCandidate::sdpMLineIndex)
            ).apply(instance, IceCandidate::new));

            public RTCIceCandidate toRtcIceCandidate() {
                return new RTCIceCandidate(this.sdpMid, this.sdpMLineIndex, this.candidate);
            }

            @Override
            public Type type() {
                return Type.ICE_CANDIDATE;
            }
        }
    }

    /** Stable message kinds shared by all signaling backend adapters. */
    enum Type implements StringRepresentable {
        JOIN_REQUEST(() -> FriendJoin.Request.CODEC),
        JOIN_ACCEPTED(() -> FriendJoin.Accepted.CODEC),
        JOIN_REJECTED(() -> FriendJoin.Rejected.CODEC),
        INVITE_DECLINED(() -> FriendJoin.InviteDeclined.CODEC),
        OFFER(() -> WebRtc.Offer.CODEC),
        ANSWER(() -> WebRtc.Answer.CODEC),
        ICE_CANDIDATE(() -> WebRtc.IceCandidate.CODEC);

        private static final Codec<Type> CODEC = StringRepresentable.fromEnum(Type::values);
        private final Supplier<MapCodec<? extends SignalingMessage>> codec;

        Type(Supplier<MapCodec<? extends SignalingMessage>> codec) {
            this.codec = codec;
        }

        private MapCodec<? extends SignalingMessage> codec() {
            return this.codec.get();
        }

        @Override
        public @NotNull String getSerializedName() {
            return this.name();
        }
    }

    static SignalingMessage inviteDeclined(String sessionId) {
        return new FriendJoin.InviteDeclined(sessionId);
    }
}
