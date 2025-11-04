package dev.sweety.network.cloud.packet.registry;

import dev.sweety.network.cloud.messaging.exception.PacketRegistrationException;
import dev.sweety.network.cloud.packet.model.Packet;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectOpenHashMap;

import java.lang.reflect.InvocationTargetException;
import java.util.IdentityHashMap;

public class OptimizedPacketRegistry implements IPacketRegistry {

    private final Byte2ObjectOpenHashMap<RegisteredPacket> idToPacket;
    private final IdentityHashMap<Class<? extends Packet>, Byte> classToId;

    public OptimizedPacketRegistry() {
        this.idToPacket = new Byte2ObjectOpenHashMap<>();
        this.classToId = new IdentityHashMap<>();
    }

    public OptimizedPacketRegistry(final byte size) {
        this.idToPacket = new Byte2ObjectOpenHashMap<>(size);
        this.classToId = new IdentityHashMap<>(size);
    }

    @SafeVarargs
    public OptimizedPacketRegistry(Class<? extends Packet>... packets) throws PacketRegistrationException {
        this((byte) packets.length);
        registerPackets(packets);
    }

    @Override
    public void registerPacket(byte packetId, Class<? extends Packet> packet) throws PacketRegistrationException {
        if (idToPacket.containsKey(packetId))
            throw new PacketRegistrationException("PacketID already in use");

        try {
            RegisteredPacket registered = new RegisteredPacket(packet);
            idToPacket.put(packetId, registered);
            classToId.put(packet, packetId);
        } catch (NoSuchMethodException e) {
            throw new PacketRegistrationException("Cannot register packet", e);
        }
    }

    @Override
    public byte getPacketId(Class<? extends Packet> packetClass) {
        return this.classToId.getOrDefault(packetClass, (byte) -1);
    }

    @Override
    public <T extends Packet> T constructPacket(byte packetId, long timestamp, byte[] data)
            throws InvocationTargetException, InstantiationException, IllegalAccessException {
        if (!idToPacket.containsKey(packetId))
            throw new IllegalArgumentException("Unknown packet id " + packetId);

        return idToPacket.get(packetId).create(packetId, timestamp, data);
    }

    @Override
    public boolean containsPacketId(byte id) {
        return idToPacket.containsKey(id);
    }
}