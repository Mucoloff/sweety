package dev.sweety.network.cloud.packet.registry;

import dev.sweety.network.cloud.messaging.exception.PacketRegistrationException;
import dev.sweety.network.cloud.packet.model.Packet;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;

import java.lang.reflect.InvocationTargetException;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class OptimizedPacketRegistry implements IPacketRegistry {

    private final Short2ObjectOpenHashMap<RegisteredPacket> idToPacket;
    private final IdentityHashMap<Class<? extends Packet>, Short> classToId;

    public OptimizedPacketRegistry() {
        this.idToPacket = new Short2ObjectOpenHashMap<>();
        this.classToId = new IdentityHashMap<>();
    }

    public OptimizedPacketRegistry(final short size) {
        this.idToPacket = new Short2ObjectOpenHashMap<>(size);
        this.classToId = new IdentityHashMap<>(size);
    }

    @SafeVarargs
    public OptimizedPacketRegistry(Class<? extends Packet>... packets) throws PacketRegistrationException {
        this((short) packets.length);
        registerPackets(packets);
    }

    public OptimizedPacketRegistry(Map<Short, Class<? extends Packet>> packets) throws PacketRegistrationException {
        this((short) packets.size());
        registerPackets(packets);
    }

    @Override
    public void registerPacket(short packetId, Class<? extends Packet> packet) throws PacketRegistrationException {
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
    public short getPacketId(Class<? extends Packet> packetClass) {
        return this.classToId.getOrDefault(packetClass, (short) -1);
    }

    @Override
    public <T extends Packet> T constructPacket(short packetId, long timestamp, byte[] data)
            throws InvocationTargetException, InstantiationException, IllegalAccessException {
        if (!idToPacket.containsKey(packetId))
            throw new IllegalArgumentException("Unknown packet id " + packetId);

        return idToPacket.get(packetId).create(packetId, timestamp, data);
    }

    @Override
    public boolean containsPacketId(short id) {
        return idToPacket.containsKey(id);
    }

    @Override
    public Set<Class<? extends Packet>> packets() {
        return classToId.keySet();
    }
}