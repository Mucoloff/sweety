package test.testzipnet.ping;

import dev.sweety.network.cloud.packet.model.PacketTransaction;

public class PingTransaction extends PacketTransaction<PingTransaction.Ping, PingTransaction.Pong> {

    public PingTransaction(Ping request) {
        super(request);
    }

    public PingTransaction(long id, Pong response) {
        super(id, response);
    }

    public PingTransaction(short _id, long _timestamp, byte[] _data) {
        super(_id, _timestamp, _data);
    }

    @Override
    protected Ping readRequest() {
        return new Ping();
    }

    @Override
    protected Pong readResponse() {
        return new Pong();
    }

    public static class Ping extends PacketTransaction.Transaction {


    }

    public static class Pong extends PacketTransaction.Transaction {

    }


}
