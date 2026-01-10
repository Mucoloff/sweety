package dev.sweety.project.netty.ping;

import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.model.PacketTransaction;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class PingTransaction extends PacketTransaction<PingTransaction.Ping, PingTransaction.Pong> {

    public PingTransaction(final Ping request) {
        super(request);
    }

    public PingTransaction(final long id, final Pong response) {
        super(id, response);
    }

    public PingTransaction(final int _id, final long _timestamp, final byte[] _data) {
        super(_id, _timestamp, _data);
    }

    @Override
    protected Ping constructRequest() {
        return new Ping();
    }

    @Override
    protected Pong constructResponse() {
        return new Pong();
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Ping extends PacketTransaction.Transaction {

        String text;

        @Override
        public void write(final PacketBuffer buffer) {
            buffer.writeString(this.text);
        }

        @Override
        public void read(final PacketBuffer buffer) {
            this.text = buffer.readString();
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pong extends PacketTransaction.Transaction {

        String text;

        @Override
        public void write(final PacketBuffer buffer) {
            buffer.writeString(this.text);
        }

        @Override
        public void read(final PacketBuffer buffer) {
            this.text = buffer.readString();
        }

    }

}
