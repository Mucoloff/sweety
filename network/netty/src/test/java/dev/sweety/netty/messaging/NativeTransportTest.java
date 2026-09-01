package dev.sweety.netty.messaging;

import dev.sweety.netty.messaging.transport.NativeTransport;
import io.netty.channel.EventLoopGroup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class NativeTransportTest {

    @Test
    public void testNativeTransportDetection() {
        NativeTransport.Type type = NativeTransport.type();
        assertNotNull(type);

        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("mac")) {
            assertEquals(NativeTransport.Type.KQUEUE, type, "On macOS, KQueue should be detected");
        } else if (os.contains("linux")) {
            assertEquals(NativeTransport.Type.EPOLL, type, "On Linux, Epoll should be detected");
        }

        assertNotNull(NativeTransport.serverSocketChannelClass());
        assertNotNull(NativeTransport.socketChannelClass());
        assertNotNull(NativeTransport.datagramChannelClass());

        EventLoopGroup group = NativeTransport.newEventLoopGroup(1, Thread.ofPlatform().name("test-group-", 0).factory());
        assertNotNull(group);
        group.shutdownGracefully();
    }
}
