
package dev.sweety.netty.packet.model;

import dev.sweety.core.crypt.ChecksumUtils;
import dev.sweety.core.math.RandomUtils;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.buffer.io.Decoder;
import dev.sweety.netty.packet.buffer.io.Encoder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.zip.CRC32;

@Getter
public abstract class PacketTransaction<R extends PacketTransaction.Transaction, S extends PacketTransaction.Transaction> extends Packet {

    private final long requestId;
    private final R request;
    private final S response;

    public PacketTransaction(final R request) {
        super();
        this.buffer().writeVarLong(this.requestId = generateId());
        this.buffer().writeObject(this.request = request);
        this.buffer().writeObject(this.response = null);
    }

    public PacketTransaction(final long id, final S response) {
        super();
        this.buffer().writeVarLong(this.requestId = id);
        this.buffer().writeObject(this.request = null);
        this.buffer().writeObject(this.response = response);
    }

    public PacketTransaction(final int _id, final long _timestamp, final byte[] _data) {
        super(_id, _timestamp, _data);
        this.requestId = this.buffer().readVarLong();
        this.request = this.buffer().readObject(this::constructRequest);
        this.response = this.buffer().readObject(this::constructResponse);
    }

    private static long generateId() {
        CRC32 crc = ChecksumUtils.crc32(true);
        byte[] randomBytes = new byte[RandomUtils.range(8, 32)];
        RandomUtils.RANDOM.nextBytes(randomBytes);
        crc.update(randomBytes);
        return crc.getValue();
    }

    public String requestCode() {
        return "#" + Long.toHexString(requestId);
    }

    public boolean hasRequest() {
        return this.request != null;
    }

    public boolean hasResponse() {
        return this.response != null;
    }

    protected abstract R constructRequest();

    protected abstract S constructResponse();

    @Data
    @NoArgsConstructor
    public abstract static class Transaction implements Encoder, Decoder {

        @Override
        public void write(final PacketBuffer buffer) {

        }

        @Override
        public void read(final PacketBuffer buffer) {

        }

    }

}