package dev.sweety.netty.service;

import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.OptimizedPacketRegistry;
import dev.sweety.netty.packet.registry.PacketEnum;
import dev.sweety.netty.packet.registry.PacketRegistry;

/**
 * Packet id-space for the service-mesh transport ({@link ServiceMessage}, {@link ServiceIdentify}).
 * Ids sit in a reserved high band ({@code 240+}) so an application registry can merge them via
 * {@link #registerInto(PacketRegistry)} alongside its own packets without collision. {@link #REGISTRY}
 * is a standalone registry of just these packets — handy for tests and mesh-only tooling.
 */
public enum ServicePackets implements PacketEnum {
    NONE(-1, null),
    SERVICE_MESSAGE(240, ServiceMessage.class),
    SERVICE_IDENTIFY(241, ServiceIdentify.class),
    SERVICE_PING(242, ServiceHeartbeat.class);

    private final int id;
    private final Class<? extends Packet> packetClass;

    ServicePackets(int id, Class<? extends Packet> packetClass) {
        this.id = id;
        this.packetClass = packetClass;
    }

    @Override public int id() { return id; }
    @Override public Class<? extends Packet> packetClass() { return packetClass; }

    /** Registry containing only the mesh transport packets. */
    public static final PacketRegistry REGISTRY = new OptimizedPacketRegistry(values().length - 1);

    static {
        NONE.register(REGISTRY, values());
        NONE.flag();
    }

    /** Merge the mesh transport packets into an application's registry. */
    public static void registerInto(PacketRegistry registry) {
        NONE.register(registry, values());
    }

    public static ServicePackets fromId(int id) {
        return PacketEnum.getById(ServicePackets.class, id, NONE);
    }
}
