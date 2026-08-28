package dev.sweety.versioning.protocol.integrity;

import dev.sweety.netty.packet.model.PacketTransaction;

/** Progressive-integrity request/response over the Netty channel. See {@link IntegrityRequest}. */
public class IntegrityTransaction extends PacketTransaction<IntegrityRequest, IntegrityResponse> {

    public IntegrityTransaction() {}

    public IntegrityTransaction(IntegrityRequest request) {
        super(request);
    }

    public IntegrityTransaction(long id, IntegrityResponse response) {
        super(id, response);
    }

}
