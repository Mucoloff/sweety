package dev.sweety.versioning.protocol.handshake;

import dev.sweety.netty.packet.model.PacketTransaction;

public class HandshakeTransaction extends PacketTransaction<HandshakeRequest, HandshakeResponse> {

    public HandshakeTransaction(HandshakeRequest request) {
        super(request);
    }

    public HandshakeTransaction(long id, HandshakeResponse response) {
        super(id, response);
    }

    public HandshakeTransaction(int _id, long _timestamp, byte[] _data) {
        super(_id, _timestamp, _data);
    }

    @Override
    protected HandshakeRequest request() {
        return new HandshakeRequest();
    }

    @Override
    protected HandshakeResponse response() {
        return new HandshakeResponse();
    }
}
