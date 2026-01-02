
package dev.sweety.netty.packet.model;

import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.buffer.io.Decoder;
import dev.sweety.netty.packet.buffer.io.Encoder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.concurrent.ThreadLocalRandom;

@Getter
public abstract class PacketTransaction<R extends PacketTransaction.Transaction, S extends PacketTransaction.Transaction> extends Packet {

    private final long requestId;
    private final R request;
    private final S response;

    public PacketTransaction(R request) {
        super();
        this.buffer().writeLong(this.requestId = ThreadLocalRandom.current().nextLong());
        this.buffer().writeObject(this.request = request);
        this.buffer().writeObject(this.response = null);
    }

    public PacketTransaction(long id, S response) {
        super();
        this.buffer().writeLong(this.requestId = id);
        this.buffer().writeObject(this.request = null);
        this.buffer().writeObject(this.response = response);
    }

    public PacketTransaction(short _id, long _timestamp, byte[] _data) {
        super(_id, _timestamp, _data);
        this.requestId = this.buffer().readLong();
        this.request = this.buffer().readObject(this::readRequest);
        this.response = this.buffer().readObject(this::readResponse);
    }

    public boolean hasRequest() {
        return this.request != null;
    }

    public boolean hasResponse() {
        return this.response != null;
    }

    public boolean verifyResponse(long id) {
        return this.requestId == id;
    }

    protected abstract R readRequest();

    protected abstract S readResponse();

    @Data
    @NoArgsConstructor
    public abstract static class Transaction implements Encoder, Decoder {

        @Override
        public void read(PacketBuffer buffer) {

        }

        @Override
        public void write(PacketBuffer buffer) {

        }
    }

}