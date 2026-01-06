package dev.sweety.netty.packet.registry;

import dev.sweety.netty.messaging.exception.PacketRegistrationException;
import dev.sweety.netty.packet.model.Packet;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.lang.reflect.InvocationTargetException;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class OptimizedPacketRegistry implements IPacketRegistry {

    private final Int2ObjectOpenHashMap<RegisteredPacket> idToPacket;
    private final IdentityHashMap<Class<? extends Packet>, Integer> classToId;

    public OptimizedPacketRegistry() {
        this.idToPacket = new Int2ObjectOpenHashMap<>();
        this.classToId = new IdentityHashMap<>();
    }

    public OptimizedPacketRegistry(final int size) {
        this.idToPacket = new Int2ObjectOpenHashMap<>(size);
        this.classToId = new IdentityHashMap<>(size);
    }

    @SafeVarargs
    public OptimizedPacketRegistry(Class<? extends Packet>... packets) throws PacketRegistrationException {
        this(packets.length);
        registerPackets(packets);
    }

    public OptimizedPacketRegistry(Map<Integer, Class<? extends Packet>> packets) throws PacketRegistrationException {
        this(packets.size());
        registerPackets(packets);
    }

    @Override
    public void registerPacket(int packetId, Class<? extends Packet> packet) throws PacketRegistrationException {
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
    public int getPacketId(Class<? extends Packet> packetClass) {
        return this.classToId.getOrDefault(packetClass, -1);
    }

    @Override
    public <T extends Packet> T constructPacket(int packetId, long timestamp, byte[] data)
            throws InvocationTargetException, InstantiationException, IllegalAccessException {
        if (!idToPacket.containsKey(packetId))
            throw new IllegalArgumentException("Unknown packet id " + packetId);

        return idToPacket.get(packetId).create(packetId, timestamp, data);
    }

    @Override
    public boolean containsPacketId(int id) {
        return idToPacket.containsKey(id);
    }

    @Override
    public Set<Class<? extends Packet>> packets() {
        return classToId.keySet();
    }
}