package cool.muyucloud.netherlink.p2p;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.onvoid.webrtc.RTCIceCandidate;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public record SignalingMessage(Type type, String sessionId, @Nullable String sdp, WebRtc.@Nullable Candidate iceCandidate) {
    public static final Codec<SignalingMessage> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                Type.CODEC.fieldOf("type").forGetter(SignalingMessage::type),
                Codec.STRING.fieldOf("sessionId").forGetter(SignalingMessage::sessionId),
                Codec.STRING.optionalFieldOf("sdp").forGetter(message -> Optional.ofNullable(message.sdp())),
                WebRtc.Candidate.CODEC.optionalFieldOf("iceCandidate").forGetter(message -> Optional.ofNullable(message.iceCandidate()))
            )
            .apply(instance, (type, sessionId, sdp, iceCandidate) -> new SignalingMessage(type, sessionId, sdp.orElse(null), iceCandidate.orElse(null)))
    );

    public static SignalingMessage joinAccepted(String sessionId) {
        return new SignalingMessage(Type.JOIN_ACCEPTED, sessionId, null, null);
    }

    public static SignalingMessage joinRejected(String sessionId) {
        return new SignalingMessage(Type.JOIN_REJECTED, sessionId, null, null);
    }

    public static SignalingMessage answer(String sessionId, String sdp) {
        return new SignalingMessage(Type.ANSWER, sessionId, sdp, null);
    }

    public static SignalingMessage iceCandidate(String sessionId, RTCIceCandidate candidate) {
        return new SignalingMessage(Type.ICE_CANDIDATE, sessionId, null, WebRtc.Candidate.from(candidate));
    }

    public @Nullable Payload decode() {
        return switch (this.type) {
            case JOIN_REQUEST -> new FriendJoin.Request(this.sessionId);
            case JOIN_ACCEPTED -> new FriendJoin.Accepted(this.sessionId);
            case JOIN_REJECTED -> new FriendJoin.Rejected(this.sessionId);
            case INVITE_DECLINED -> FriendJoin.InviteDeclined.INSTANCE;
            case OFFER -> this.sdp != null ? new WebRtc.Offer(this.sessionId, this.sdp) : null;
            case ANSWER -> this.sdp != null ? new WebRtc.Answer(this.sessionId, this.sdp) : null;
            case ICE_CANDIDATE -> this.iceCandidate != null
                ? new WebRtc.IceCandidate(this.sessionId, this.iceCandidate)
                : (this.sdp != null ? new WebRtc.IceCandidate(this.sessionId, new WebRtc.Candidate(this.sdp, "0", 0)) : null);
        };
    }

    public sealed interface Payload permits FriendJoin, WebRtc {
    }

    public sealed interface FriendJoin extends Payload permits FriendJoin.Request, FriendJoin.Accepted, FriendJoin.Rejected, FriendJoin.InviteDeclined {
        record Request(String sessionId) implements FriendJoin {
        }

        record Accepted(String sessionId) implements FriendJoin {
        }

        record Rejected(String sessionId) implements FriendJoin {
        }

        record InviteDeclined() implements FriendJoin {
            private static final InviteDeclined INSTANCE = new InviteDeclined();
        }
    }

    public sealed interface WebRtc extends Payload permits WebRtc.Offer, WebRtc.Answer, WebRtc.IceCandidate {
        String sessionId();

        record Offer(String sessionId, String sdp) implements WebRtc {
        }

        record Answer(String sessionId, String sdp) implements WebRtc {
        }

        record IceCandidate(String sessionId, Candidate candidate) implements WebRtc {
        }

        record Candidate(String candidate, @Nullable String sdpMid, int sdpMLineIndex) {
            private static final Codec<Candidate> CODEC = RecordCodecBuilder.create(
                instance -> instance.group(
                        Codec.STRING.fieldOf("candidate").forGetter(Candidate::candidate),
                        Codec.STRING.optionalFieldOf("sdpMid").forGetter(candidate -> Optional.ofNullable(candidate.sdpMid())),
                        Codec.INT.fieldOf("sdpMLineIndex").forGetter(Candidate::sdpMLineIndex)
                    )
                    .apply(instance, (candidate, sdpMid, sdpMLineIndex) -> new Candidate(candidate, sdpMid.orElse(null), sdpMLineIndex))
            );

            private static Candidate from(RTCIceCandidate candidate) {
                return new Candidate(candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex);
            }

            public RTCIceCandidate toRtcIceCandidate() {
                return new RTCIceCandidate(this.sdpMid != null ? this.sdpMid : "0", this.sdpMLineIndex, this.candidate);
            }
        }
    }

    public enum Type implements StringRepresentable {
        JOIN_REQUEST,
        JOIN_ACCEPTED,
        JOIN_REJECTED,
        INVITE_DECLINED,
        OFFER,
        ANSWER,
        ICE_CANDIDATE;

        private static final Codec<Type> CODEC = StringRepresentable.fromEnum(Type::values);

        @Override
        public String getSerializedName() {
            return this.name();
        }
    }
}
