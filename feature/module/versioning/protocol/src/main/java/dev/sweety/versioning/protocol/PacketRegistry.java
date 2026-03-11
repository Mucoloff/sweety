package dev.sweety.versioning.protocol;

import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import dev.sweety.netty.packet.registry.OptimizedPacketRegistry;
import dev.sweety.netty.packet.registry.PacketEnum;
import dev.sweety.versioning.protocol.handshake.HandshakeTransaction;
import dev.sweety.versioning.protocol.update.ReleasePacket;
import org.jetbrains.annotations.ApiStatus;

public enum PacketRegistry implements PacketEnum {
    NONE(-1, null),

    HANDSHAKE(1, HandshakeTransaction.class),
    RELEASE(2, ReleasePacket.class),

    ;

    private final int id;
    private final Class<? extends Packet> packetClass;

    @ApiStatus.Experimental
    PacketRegistry(int id) {
        this(id, null);
        add();
    }

    PacketRegistry(int id, Class<? extends Packet> packetClass) {
        this.id = id;
        this.packetClass = packetClass;
    }

    public static final IPacketRegistry REGISTRY = new OptimizedPacketRegistry(values().length - 2);

    static {
        NONE.register(REGISTRY, values());
        NONE.flag();
    }

    public static PacketRegistry fromId(int id) {
        return PacketEnum.getById(PacketRegistry.class, id, NONE);
    }

    @Override
    public int id() {
        return id;
    }

    @Override
    public Class<? extends Packet> packetClass() {
        return packetClass;
    }
}
