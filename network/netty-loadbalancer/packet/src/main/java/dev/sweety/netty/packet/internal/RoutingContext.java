package dev.sweety.netty.packet.internal;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.math.pool.ObjectPool;
import dev.sweety.netty.packet.buffer.io.Codec;

import java.util.UUID;

/**
 * Polymorphic, zero-allocation routing context for Netty packet dispatch.
 * Backed by ThreadLocal ObjectPools to prevent JVM young-gen GC pressure.
 */
public interface RoutingContext extends Codec {

    byte TYPE_EMPTY = 0;
    byte TYPE_MODEL = 1;
    byte TYPE_SHARD = 2;
    byte TYPE_SESSION = 3;
    byte TYPE_COMPOSITE = 4;

    byte typeId();

    void reset();

    default void release() {
        RoutingContextPools.release(this);
    }

    static RoutingContext empty() {
        return EmptyRoutingContext.INSTANCE;
    }

    static ModelRoutingContext ofModel(String modelName) {
        return ofModel(modelName, 0);
    }

    static ModelRoutingContext ofModel(String modelName, int priority) {
        ModelRoutingContext ctx = RoutingContextPools.MODEL_POOL.acquire();
        ctx.modelName(modelName);
        ctx.priority(priority);
        return ctx;
    }

    static ShardRoutingContext ofShard(long shardKey) {
        ShardRoutingContext ctx = RoutingContextPools.SHARD_POOL.acquire();
        ctx.shardKey(shardKey);
        return ctx;
    }

    static SessionRoutingContext ofSession(UUID sessionId) {
        return ofSession(sessionId, 0);
    }

    static SessionRoutingContext ofSession(UUID sessionId, int priority) {
        SessionRoutingContext ctx = RoutingContextPools.SESSION_POOL.acquire();
        ctx.clientSessionId(sessionId);
        ctx.priority(priority);
        return ctx;
    }

    static CompositeRoutingContext ofComposite() {
        return RoutingContextPools.COMPOSITE_POOL.acquire();
    }

    static RoutingContext readContext(BufferReader buffer) {
        final byte typeId = buffer.readByte();
        return switch (typeId) {
            case TYPE_EMPTY -> EmptyRoutingContext.INSTANCE;
            case TYPE_MODEL -> {
                ModelRoutingContext ctx = RoutingContextPools.MODEL_POOL.acquire();
                ctx.readBody(buffer);
                yield ctx;
            }
            case TYPE_SHARD -> {
                ShardRoutingContext ctx = RoutingContextPools.SHARD_POOL.acquire();
                ctx.readBody(buffer);
                yield ctx;
            }
            case TYPE_SESSION -> {
                SessionRoutingContext ctx = RoutingContextPools.SESSION_POOL.acquire();
                ctx.readBody(buffer);
                yield ctx;
            }
            case TYPE_COMPOSITE -> {
                CompositeRoutingContext ctx = RoutingContextPools.COMPOSITE_POOL.acquire();
                ctx.readBody(buffer);
                yield ctx;
            }
            default -> throw new IllegalArgumentException("Unknown RoutingContext typeId: " + typeId);
        };
    }

    final class EmptyRoutingContext implements RoutingContext {
        public static final EmptyRoutingContext INSTANCE = new EmptyRoutingContext();

        private EmptyRoutingContext() {}

        @Override public byte typeId() { return TYPE_EMPTY; }
        @Override public void reset() {}
        @Override public void release() {} // Singleton, no-op release

        @Override public void write(BufferWriter buffer) { buffer.writeByte(TYPE_EMPTY); }
        @Override public void read(BufferReader buffer) {}
    }

    final class ModelRoutingContext implements RoutingContext {
        private String modelName;
        private int priority;

        public ModelRoutingContext() {}

        @Override public byte typeId() { return TYPE_MODEL; }

        @Override
        public void reset() {
            this.modelName = null;
            this.priority = 0;
        }

        @Override
        public void write(BufferWriter buffer) {
            buffer.writeByte(TYPE_MODEL);
            buffer.writeString(this.modelName != null ? this.modelName : "");
            buffer.writeVarInt(this.priority);
        }

        @Override
        public void read(BufferReader buffer) {
            // Header read externally
            readBody(buffer);
        }

        public void readBody(BufferReader buffer) {
            final String m = buffer.readString();
            this.modelName = m.isEmpty() ? null : m;
            this.priority = buffer.readVarInt();
        }

        public String modelName() { return modelName; }
        public ModelRoutingContext modelName(String modelName) { this.modelName = modelName; return this; }
        public int priority() { return priority; }
        public ModelRoutingContext priority(int priority) { this.priority = priority; return this; }
    }

    final class ShardRoutingContext implements RoutingContext {
        private long shardKey;

        public ShardRoutingContext() {}

        @Override public byte typeId() { return TYPE_SHARD; }

        @Override
        public void reset() {
            this.shardKey = 0L;
        }

        @Override
        public void write(BufferWriter buffer) {
            buffer.writeByte(TYPE_SHARD);
            buffer.writeVarLong(this.shardKey);
        }

        @Override
        public void read(BufferReader buffer) {
            readBody(buffer);
        }

        public void readBody(BufferReader buffer) {
            this.shardKey = buffer.readVarLong();
        }

        public long shardKey() { return shardKey; }
        public ShardRoutingContext shardKey(long shardKey) { this.shardKey = shardKey; return this; }
    }

    final class SessionRoutingContext implements RoutingContext {
        private UUID clientSessionId;
        private int priority;

        public SessionRoutingContext() {}

        @Override public byte typeId() { return TYPE_SESSION; }

        @Override
        public void reset() {
            this.clientSessionId = null;
            this.priority = 0;
        }

        @Override
        public void write(BufferWriter buffer) {
            buffer.writeByte(TYPE_SESSION);
            buffer.writeBoolean(this.clientSessionId != null);
            if (this.clientSessionId != null) {
                buffer.writeLong(this.clientSessionId.getMostSignificantBits());
                buffer.writeLong(this.clientSessionId.getLeastSignificantBits());
            }
            buffer.writeVarInt(this.priority);
        }

        @Override
        public void read(BufferReader buffer) {
            readBody(buffer);
        }

        public void readBody(BufferReader buffer) {
            if (buffer.readBoolean()) {
                this.clientSessionId = new UUID(buffer.readLong(), buffer.readLong());
            } else {
                this.clientSessionId = null;
            }
            this.priority = buffer.readVarInt();
        }

        public UUID clientSessionId() { return clientSessionId; }
        public SessionRoutingContext clientSessionId(UUID id) { this.clientSessionId = id; return this; }
        public int priority() { return priority; }
        public SessionRoutingContext priority(int p) { this.priority = p; return this; }
    }

    final class CompositeRoutingContext implements RoutingContext {
        private String modelName;
        private long shardKey;
        private UUID clientSessionId;
        private int priority;
        private int flags;

        public CompositeRoutingContext() {}

        @Override public byte typeId() { return TYPE_COMPOSITE; }

        @Override
        public void reset() {
            this.modelName = null;
            this.shardKey = 0L;
            this.clientSessionId = null;
            this.priority = 0;
            this.flags = 0;
        }

        @Override
        public void write(BufferWriter buffer) {
            buffer.writeByte(TYPE_COMPOSITE);
            buffer.writeString(this.modelName != null ? this.modelName : "");
            buffer.writeVarLong(this.shardKey);
            buffer.writeBoolean(this.clientSessionId != null);
            if (this.clientSessionId != null) {
                buffer.writeLong(this.clientSessionId.getMostSignificantBits());
                buffer.writeLong(this.clientSessionId.getLeastSignificantBits());
            }
            buffer.writeVarInt(this.priority);
            buffer.writeVarInt(this.flags);
        }

        @Override
        public void read(BufferReader buffer) {
            readBody(buffer);
        }

        public void readBody(BufferReader buffer) {
            final String m = buffer.readString();
            this.modelName = m.isEmpty() ? null : m;
            this.shardKey = buffer.readVarLong();
            if (buffer.readBoolean()) {
                this.clientSessionId = new UUID(buffer.readLong(), buffer.readLong());
            } else {
                this.clientSessionId = null;
            }
            this.priority = buffer.readVarInt();
            this.flags = buffer.readVarInt();
        }

        public String modelName() { return modelName; }
        public CompositeRoutingContext modelName(String m) { this.modelName = m; return this; }
        public long shardKey() { return shardKey; }
        public CompositeRoutingContext shardKey(long k) { this.shardKey = k; return this; }
        public UUID clientSessionId() { return clientSessionId; }
        public CompositeRoutingContext clientSessionId(UUID id) { this.clientSessionId = id; return this; }
        public int priority() { return priority; }
        public CompositeRoutingContext priority(int p) { this.priority = p; return this; }
        public int flags() { return flags; }
        public CompositeRoutingContext flags(int f) { this.flags = f; return this; }
    }

    final class RoutingContextPools {
        public static final ObjectPool<ModelRoutingContext> MODEL_POOL =
                ObjectPool.threadLocal(ModelRoutingContext::new).reset(ModelRoutingContext::reset).build();

        public static final ObjectPool<ShardRoutingContext> SHARD_POOL =
                ObjectPool.threadLocal(ShardRoutingContext::new).reset(ShardRoutingContext::reset).build();

        public static final ObjectPool<SessionRoutingContext> SESSION_POOL =
                ObjectPool.threadLocal(SessionRoutingContext::new).reset(SessionRoutingContext::reset).build();

        public static final ObjectPool<CompositeRoutingContext> COMPOSITE_POOL =
                ObjectPool.threadLocal(CompositeRoutingContext::new).reset(CompositeRoutingContext::reset).build();

        public static void release(RoutingContext ctx) {
            if (ctx == null) return;
            switch (ctx.typeId()) {
                case TYPE_MODEL -> MODEL_POOL.release((ModelRoutingContext) ctx);
                case TYPE_SHARD -> SHARD_POOL.release((ShardRoutingContext) ctx);
                case TYPE_SESSION -> SESSION_POOL.release((SessionRoutingContext) ctx);
                case TYPE_COMPOSITE -> COMPOSITE_POOL.release((CompositeRoutingContext) ctx);
                default -> {} // TYPE_EMPTY is singleton
            }
        }
    }
}
