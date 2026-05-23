package cool.muyucloud.netherlink.p2p;

import cool.muyucloud.netherlink.NliConstants;
import dev.onvoid.webrtc.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.util.AttributeKey;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

public final class RtcChannel extends AbstractChannel {
    private static final ChannelMetadata METADATA = new ChannelMetadata(false);
    private static final int MAX_CHUNK_SIZE = 262144;
    private static final long HIGH_WATER_MARK = 1048576L;
    private static final long LOW_WATER_MARK = 262144L;
    private static final int BACKPRESSURE_FLAG = 1;
    private static final AttributeKey<Boolean> SECURE_TRANSPORT = AttributeKey.valueOf("secure_transport");

    private final RtcHandshake.HandshakeResult handshakeResult;
    private final ChannelConfig config = new DefaultChannelConfig(this);
    private volatile boolean closed;
    private volatile boolean activated;
    private boolean writeStalled;

    public RtcChannel(RtcHandshake.HandshakeResult handshakeResult) {
        super(null);
        this.handshakeResult = handshakeResult;
        this.attr(SECURE_TRANSPORT).set(Boolean.TRUE);
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    public ChannelConfig config() {
        return config;
    }

    @Override
    protected AbstractUnsafe newUnsafe() {
        return new RtcUnsafe();
    }

    @Override
    protected boolean isCompatible(EventLoop loop) {
        return loop instanceof SingleThreadEventLoop;
    }

    @Override
    public boolean isOpen() {
        return !closed;
    }

    @Override
    public boolean isActive() {
        return activated && !closed;
    }

    @Override
    protected SocketAddress localAddress0() {
        return new InetSocketAddress("rtc-local", 0);
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return new InetSocketAddress("rtc-remote", 0);
    }

    @Override
    protected void doRegister() {
        RTCDataChannelState initial = this.handshakeResult.dataChannel().getState();
        NliConstants.LOG.info("[P2P-Netty] Registering RtcChannel, initial DataChannel state={}", initial);
        this.eventLoop().execute(() -> {
            this.handleStateChange(initial);
            this.handshakeResult.dataChannel().registerObserver(new RTCDataChannelObserver() {
                @Override
                public void onMessage(RTCDataChannelBuffer buffer) {
                    try {
                        NliConstants.LOG.debug("[P2P-Netty] Received DataChannel message bytes={}", buffer.data.remaining());
                        ByteBuf copy = Unpooled.copiedBuffer(buffer.data);
                        RtcChannel.this.eventLoop().execute(() -> {
                            try {
                                RtcChannel.this.handleMessage(copy);
                            } catch (Throwable error) {
                                NliConstants.LOG.error("[P2P-Netty] Failed to handle inbound DataChannel message", error);
                                copy.release();
                                RtcChannel.this.pipeline().fireExceptionCaught(error);
                            }
                        });
                    } catch (Throwable error) {
                        NliConstants.LOG.error("[P2P-Netty] Failed while receiving DataChannel message", error);
                    }
                }

                @Override
                public void onStateChange() {
                    try {
                        RTCDataChannelState state = RtcChannel.this.handshakeResult.dataChannel().getState();
                        NliConstants.LOG.info("[P2P-Netty] DataChannel state -> {}", state);
                        RtcChannel.this.eventLoop().execute(() -> {
                            try {
                                RtcChannel.this.handleStateChange(state);
                            } catch (Throwable error) {
                                NliConstants.LOG.error("[P2P-Netty] Failed to handle DataChannel state {}", state, error);
                                RtcChannel.this.pipeline().fireExceptionCaught(error);
                            }
                        });
                    } catch (Throwable error) {
                        NliConstants.LOG.error("[P2P-Netty] Failed while processing DataChannel state change", error);
                    }
                }

                @Override
                public void onBufferedAmountChange(long previousAmount) {
                    if (RtcChannel.this.handshakeResult.dataChannel().getBufferedAmount() <= LOW_WATER_MARK) {
                        RtcChannel.this.eventLoop().execute(() -> RtcChannel.this.setWriteStalled(false));
                    }
                }
            });
        });
    }

    @Override
    protected void doBind(SocketAddress localAddress) {
        throw new UnsupportedOperationException("RtcChannel cannot be bound");
    }

    @Override
    protected void doDisconnect() {
        this.closeFromTransport();
    }

    @Override
    protected void doClose() {
        if (!closed) {
            NliConstants.LOG.info("[P2P-Netty] Closing RtcChannel");
            closed = true;
            dispose(this.handshakeResult);
        }
    }

    @Override
    protected void doBeginRead() {
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        Object message;
        while ((message = in.current()) != null) {
            if (message instanceof ByteBuf buffer) {
                this.writeByteBuf(buffer);
            }
            in.remove();
            if (this.handshakeResult.dataChannel().getBufferedAmount() >= HIGH_WATER_MARK) {
                this.setWriteStalled(true);
                return;
            }
        }
    }

    private void writeByteBuf(ByteBuf buffer) throws Exception {
        int remaining = buffer.readableBytes();
        int index = buffer.readerIndex();
        while (remaining > 0) {
            int chunk = Math.min(remaining, MAX_CHUNK_SIZE);
            byte[] bytes = new byte[chunk];
            buffer.getBytes(index, bytes);
            try {
                this.handshakeResult.dataChannel().send(new RTCDataChannelBuffer(ByteBuffer.wrap(bytes), true));
            } catch (RuntimeException e) {
                NliConstants.LOG.error("[P2P-Netty] DataChannel send failed chunk={} remaining={}", chunk, remaining, e);
                throw e;
            }
            NliConstants.LOG.debug("[P2P-Netty] Sent DataChannel chunk bytes={}", chunk);
            index += chunk;
            remaining -= chunk;
        }
    }

    private void handleMessage(ByteBuf buffer) {
        NliConstants.LOG.debug(
            "[P2P-Netty] Handling inbound bytes={} closed={} activated={} autoRead={}",
            buffer.readableBytes(),
            closed,
            activated,
            this.config.isAutoRead()
        );
        if (!closed && activated && this.config.isAutoRead()) {
            this.pipeline().fireChannelRead(buffer);
            this.pipeline().fireChannelReadComplete();
        } else {
            buffer.release();
        }
    }

    private void handleStateChange(RTCDataChannelState state) {
        if (closed) {
            return;
        }
        switch (state) {
            case OPEN -> {
                if (!activated) {
                    NliConstants.LOG.info("[P2P-Netty] Channel active");
                    activated = true;
                    this.pipeline().fireChannelActive();
                }
            }
            case CLOSING, CLOSED -> this.closeFromTransport();
        }
    }

    private void setWriteStalled(boolean stalled) {
        if (!closed && stalled != this.writeStalled) {
            this.writeStalled = stalled;
            ChannelOutboundBuffer outbound = this.unsafe().outboundBuffer();
            if (outbound != null) {
                outbound.setUserDefinedWritability(BACKPRESSURE_FLAG, !stalled);
            }
            if (!stalled) {
                this.unsafe().flush();
            }
        }
    }

    private void closeFromTransport() {
        if (!closed) {
            NliConstants.LOG.info("[P2P-Netty] Closing from transport");
            this.unsafe().close(this.voidPromise());
        }
    }

    public static void dispose(RtcHandshake.HandshakeResult handshakeResult) {
        dispose(handshakeResult.peerConnection(), handshakeResult.dataChannel());
    }

    public static void dispose(RTCPeerConnection peerConnection, @Nullable RTCDataChannel dataChannel) {
        if (dataChannel != null) {
            try {
                dataChannel.unregisterObserver();
                dataChannel.close();
                dataChannel.dispose();
            } catch (RuntimeException e) {
                NliConstants.LOG.warn("RtcChannel dataChannel dispose threw", e);
            }
        }
        try {
            peerConnection.close();
        } catch (RuntimeException e) {
            NliConstants.LOG.warn("RtcChannel peerConnection close threw", e);
        }
    }

    private final class RtcUnsafe extends AbstractUnsafe {
        @Override
        public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            promise.setFailure(new UnsupportedOperationException("RtcChannel is already connected"));
        }
    }
}
