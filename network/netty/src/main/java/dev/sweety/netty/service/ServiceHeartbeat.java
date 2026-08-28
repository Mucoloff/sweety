package dev.sweety.netty.service;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.netty.packet.model.Packet;

/**
 * Transport-level liveness probe between a {@link ServiceClient} and the {@link HubServer} — sent and
 * answered directly (never routed through {@link ServiceMessage}, since the hub has no service id of its
 * own). The edge pings on an interval; the hub echoes a pong immediately on receipt.
 */
public final class ServiceHeartbeat extends Packet {

    // Content is identical either way — direction is implied by which side received the packet, never
    // read back off it — so ping() and pong() share one instance per thread; the two names exist purely
    // for call-site clarity (a HubServer reading "ping()" at a send site would be confusing).
    private static final ThreadLocal<ServiceHeartbeat> INSTANCE = ThreadLocal.withInitial(ServiceHeartbeat::new);

    public static ServiceHeartbeat ping() {
        return INSTANCE.get();
    }

    public static ServiceHeartbeat pong() {
        return INSTANCE.get();
    }

    public ServiceHeartbeat() {
    }

    @Override
    public void read(BufferReader buffer) {

    }

    @Override
    public void write(BufferWriter buffer) {

    }
}
