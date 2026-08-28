package dev.sweety.netty.service;

import dev.sweety.netty.packet.model.PacketTransaction;

/**
 * The routed unit on the service mesh: a transaction whose request and response are both a
 * {@link ServiceEnvelope}. Correlation reuses the base {@code requestId}: {@link #FIRE_AND_FORGET_ID}
 * ({@code 0}) marks a one-way message, a random id marks an RPC request, and the same id on the
 * response leg lets the originator's {@code TransactionManager} complete the pending future. The
 * {@link HubServer} routes purely on the envelope's {@code receiverId} without decoding the payload.
 */
public final class ServiceMessage extends PacketTransaction<ServiceEnvelope, ServiceEnvelope> {

    /** Request id reserved for one-way (no-reply) messages. */
    public static final long FIRE_AND_FORGET_ID = 0L;

    public ServiceMessage() { super(); }
    /** RPC request — generates a random correlation id. */
    public ServiceMessage(ServiceEnvelope request) { super(request); }
    /** Response leg — echoes the request's correlation id. */
    public ServiceMessage(long id, ServiceEnvelope response) { super(id, response); }

    private ServiceMessage(long id, ServiceEnvelope request, boolean asRequest) { super(id, request, asRequest); }

    /** One-way message (no reply, no pending future). */
    public static ServiceMessage fireAndForget(ServiceEnvelope request) {
        return new ServiceMessage(FIRE_AND_FORGET_ID, request, true);
    }

    public boolean isFireAndForget() { return getRequestId() == FIRE_AND_FORGET_ID; }
}
