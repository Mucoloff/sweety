package dev.sweety.netty.packet;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.internal.RoutingContext;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class RoutingContextTest {

    @Test
    public void testEmptyRoutingContext() {
        RoutingContext empty = RoutingContext.empty();
        assertEquals(RoutingContext.TYPE_EMPTY, empty.typeId());

        PacketBuffer buffer = new PacketBuffer();
        empty.write(buffer);

        RoutingContext read = RoutingContext.readContext(buffer);
        assertEquals(RoutingContext.TYPE_EMPTY, read.typeId());
        assertSame(RoutingContext.EmptyRoutingContext.INSTANCE, read);
    }

    @Test
    public void testModelRoutingContextPooling() {
        RoutingContext.ModelRoutingContext ctx = RoutingContext.ofModel("crystal-v1", 2);
        assertEquals(RoutingContext.TYPE_MODEL, ctx.typeId());
        assertEquals("crystal-v1", ctx.modelName());
        assertEquals(2, ctx.priority());

        PacketBuffer buffer = new PacketBuffer();
        ctx.write(buffer);
        ctx.release(); // recycled to pool

        RoutingContext read = RoutingContext.readContext(buffer);
        assertInstanceOf(RoutingContext.ModelRoutingContext.class, read);
        RoutingContext.ModelRoutingContext modelRead = (RoutingContext.ModelRoutingContext) read;
        assertEquals("crystal-v1", modelRead.modelName());
        assertEquals(2, modelRead.priority());
        modelRead.release();
    }

    @Test
    public void testShardRoutingContext() {
        RoutingContext.ShardRoutingContext ctx = RoutingContext.ofShard(123456789L);
        assertEquals(RoutingContext.TYPE_SHARD, ctx.typeId());
        assertEquals(123456789L, ctx.shardKey());

        PacketBuffer buffer = new PacketBuffer();
        ctx.write(buffer);
        ctx.release();

        RoutingContext read = RoutingContext.readContext(buffer);
        assertInstanceOf(RoutingContext.ShardRoutingContext.class, read);
        RoutingContext.ShardRoutingContext shardRead = (RoutingContext.ShardRoutingContext) read;
        assertEquals(123456789L, shardRead.shardKey());
        shardRead.release();
    }

    @Test
    public void testSessionRoutingContext() {
        UUID sessionId = UUID.randomUUID();
        RoutingContext.SessionRoutingContext ctx = RoutingContext.ofSession(sessionId, 5);
        assertEquals(RoutingContext.TYPE_SESSION, ctx.typeId());
        assertEquals(sessionId, ctx.clientSessionId());
        assertEquals(5, ctx.priority());

        PacketBuffer buffer = new PacketBuffer();
        ctx.write(buffer);
        ctx.release();

        RoutingContext read = RoutingContext.readContext(buffer);
        assertInstanceOf(RoutingContext.SessionRoutingContext.class, read);
        RoutingContext.SessionRoutingContext sessionRead = (RoutingContext.SessionRoutingContext) read;
        assertEquals(sessionId, sessionRead.clientSessionId());
        assertEquals(5, sessionRead.priority());
        sessionRead.release();
    }
}
