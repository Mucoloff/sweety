package dev.sweety.versioning.protocol.handshake;

import dev.sweety.netty.packet.model.PacketTransaction;

public class HandshakeTransaction extends PacketTransaction<HandshakeRequest, HandshakeResponse> {

    public HandshakeTransaction() {}

    public HandshakeTransaction(HandshakeRequest request) {
        super(request);
    }

    public HandshakeTransaction(long id, HandshakeResponse response) {
        super(id, response);
    }

}
