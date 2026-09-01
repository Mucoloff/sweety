package dev.sweety.netty.messaging.leak;

import dev.sweety.math.pool.leak.ResourceLeakDetector;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.model.Packet;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

/**
 * Netty ChannelHandler that hooks into Sweety's {@link ResourceLeakDetector} to track
 * pooled packets and {@link PacketBuffer} lifecycles across Netty pipelines.
 */
@ChannelHandler.Sharable
public final class PacketLeakDetectionHandler extends ChannelDuplexHandler {

    private static final ResourceLeakDetector<Object> DETECTOR = new ResourceLeakDetector<>(Object.class);

    private static final PacketLeakDetectionHandler INSTANCE = new PacketLeakDetectionHandler();

    public static PacketLeakDetectionHandler instance() {
        return INSTANCE;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        trackIfPooled(msg);
        ctx.fireChannelRead(msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        ResourceLeakDetector.ResourceLeakTracker<Object> tracker = trackIfPooled(msg);
        if (tracker != null) {
            promise.addListener(future -> tracker.close(msg));
        }
        ctx.write(msg, promise);
    }

    private ResourceLeakDetector.ResourceLeakTracker<Object> trackIfPooled(Object msg) {
        if (msg == null) return null;
        if (msg instanceof Packet || msg instanceof PacketBuffer) {
            return DETECTOR.track(msg);
        }
        return null;
    }

    public static ResourceLeakDetector<Object> getDetector() {
        return DETECTOR;
    }
}
