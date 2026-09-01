package dev.sweety.netty.messaging.security;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RateLimitHandlerTest {

    @Test
    public void testTokenBucketConsumptionAndBurst() {
        TokenBucket bucket = new TokenBucket(5, 10.0); // 5 burst, 10 tokens/sec

        // Consume all 5 initial tokens
        for (int i = 0; i < 5; i++) {
            assertTrue(bucket.tryConsume(), "Should consume token " + i);
        }

        // 6th consume should fail immediately (bucket empty)
        assertFalse(bucket.tryConsume(), "Burst exceeded, should fail");
    }

    @Test
    public void testRateLimitHandlerDropsExcessPackets() {
        RateLimitHandler handler = RateLimitHandler.perChannel(3, 10.0);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        // 3 messages allowed
        assertTrue(channel.writeInbound(Unpooled.wrappedBuffer(new byte[]{1})));
        assertTrue(channel.writeInbound(Unpooled.wrappedBuffer(new byte[]{2})));
        assertTrue(channel.writeInbound(Unpooled.wrappedBuffer(new byte[]{3})));

        // Drain the 3 allowed messages
        assertNotNull(channel.readInbound());
        assertNotNull(channel.readInbound());
        assertNotNull(channel.readInbound());
        assertNull(channel.readInbound());

        // 4th message should be dropped (writeInbound returns false as queue is empty)
        assertFalse(channel.writeInbound(Unpooled.wrappedBuffer(new byte[]{4})));
        assertNull(channel.readInbound(), "4th message was dropped");
    }
}
