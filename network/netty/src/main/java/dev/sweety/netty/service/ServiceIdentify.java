package dev.sweety.netty.service;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.netty.packet.model.PacketTransaction;

/**
 * Identify handshake on the service mesh: a {@link ServiceClient} sends {@link ServiceState#REQUEST}
 * carrying its numeric service id on (re)connect; the {@link HubServer} registers the channel under
 * that id and answers {@link ServiceState#ACCEPT} (or {@link ServiceState#REJECT}) with the same
 * request id, completing the client's identify future. Request and response are both {@link Handshake}.
 */
public final class ServiceIdentify extends PacketTransaction<ServiceIdentify.Handshake, ServiceIdentify.Handshake> {

    public ServiceIdentify() { super(); }
    public ServiceIdentify(Handshake request) { super(request); }
    public ServiceIdentify(long id, Handshake response) { super(id, response); }

    @Override protected Handshake request() { return new Handshake(); }
    @Override protected Handshake response() { return new Handshake(); }

    public static final class Handshake extends PacketTransaction.Transaction {
        private int serviceId;
        private ServiceState state = ServiceState.REQUEST;
        private String secret = "";

        public Handshake() {}
        public Handshake(int serviceId, ServiceState state) {
            this(serviceId, state, "");
        }
        public Handshake(int serviceId, ServiceState state, String secret) {
            this.serviceId = serviceId;
            this.state = state != null ? state : ServiceState.REQUEST;
            this.secret = secret != null ? secret : "";
        }

        public int serviceId() { return serviceId; }
        public ServiceState state() { return state; }
        /** Shared secret presented on {@link ServiceState#REQUEST}; empty if the mesh has none configured. */
        public String secret() { return secret; }

        @Override
        public void write(BufferWriter buffer) {
            buffer.writeVarInt(serviceId);
            buffer.writeEnum(state);
            buffer.writeString(secret);
        }

        @Override
        public void read(BufferReader buffer) {
            serviceId = buffer.readVarInt();
            state = buffer.readEnum(ServiceState.class);
            secret = buffer.readString();
        }
    }
}
