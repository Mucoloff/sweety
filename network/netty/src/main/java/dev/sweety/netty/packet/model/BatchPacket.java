package dev.sweety.netty.packet.model;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.netty.feature.batch.Batch;

import java.util.function.Function;

public class BatchPacket extends Packet {

    private Batch batch;

    public BatchPacket() {}

    public BatchPacket(final Function<Class<? extends Packet>, Integer> idMap, final Packet... packets) {
        this.batch = new Batch(idMap, p -> p instanceof BatchPacket, packets);
    }

    @Override
    public void write(final BufferWriter buffer) {
        this.batch.write(buffer);
    }

    @Override
    public void read(final BufferReader buffer) {
        this.batch = new Batch();
        this.batch.read(buffer);
        // --- v1 tail ---
    }

    public Packet[] decode(final Batch.Constructor constructor) {
        if (this.batch == null) return new Packet[0];
        return this.batch.decode(constructor);
    }

    public byte[] rawBatchBytes() {
        return this.batch != null ? this.batch.rawBatchBytes() : new byte[0];
    }

    public boolean isDecoded() {
        return this.batch != null && this.batch.isDecoded();
    }
}
