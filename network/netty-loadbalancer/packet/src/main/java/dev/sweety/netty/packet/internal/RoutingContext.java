package dev.sweety.netty.packet.internal;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.netty.packet.buffer.io.Codec;

import java.util.UUID;

public class RoutingContext implements Codec {

    private String modelName;
    private long shardKey;
    private UUID clientSessionId;
    private int priority;
    private int flags;

    public RoutingContext() {
    }

    public RoutingContext(String modelName, long shardKey, UUID clientSessionId, int priority, int flags) {
        this.modelName = modelName;
        this.shardKey = shardKey;
        this.clientSessionId = clientSessionId;
        this.priority = priority;
        this.flags = flags;
    }

    public static RoutingContext ofModel(String modelName) {
        RoutingContext ctx = new RoutingContext();
        ctx.modelName = modelName;
        return ctx;
    }

    public static RoutingContext ofShard(long shardKey) {
        RoutingContext ctx = new RoutingContext();
        ctx.shardKey = shardKey;
        return ctx;
    }

    public static RoutingContext ofSession(UUID sessionId) {
        RoutingContext ctx = new RoutingContext();
        ctx.clientSessionId = sessionId;
        return ctx;
    }

    @Override
    public void write(BufferWriter buffer) {
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
        final String model = buffer.readString();
        this.modelName = model.isEmpty() ? null : model;
        this.shardKey = buffer.readVarLong();
        if (buffer.readBoolean()) {
            this.clientSessionId = new UUID(buffer.readLong(), buffer.readLong());
        } else {
            this.clientSessionId = null;
        }
        this.priority = buffer.readVarInt();
        this.flags = buffer.readVarInt();
    }

    public String modelName() {
        return modelName;
    }

    public RoutingContext modelName(String modelName) {
        this.modelName = modelName;
        return this;
    }

    public long shardKey() {
        return shardKey;
    }

    public RoutingContext shardKey(long shardKey) {
        this.shardKey = shardKey;
        return this;
    }

    public UUID clientSessionId() {
        return clientSessionId;
    }

    public RoutingContext clientSessionId(UUID clientSessionId) {
        this.clientSessionId = clientSessionId;
        return this;
    }

    public int priority() {
        return priority;
    }

    public RoutingContext priority(int priority) {
        this.priority = priority;
        return this;
    }

    public int flags() {
        return flags;
    }

    public RoutingContext flags(int flags) {
        this.flags = flags;
        return this;
    }
}
