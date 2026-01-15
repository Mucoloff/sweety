package dev.sweety.netty.packet.registry;

import dev.sweety.netty.messaging.exception.PacketRegistrationException;
import dev.sweety.netty.packet.model.Packet;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SimplePacketRegistry implements IPacketRegistry {

    private final Map<Integer, RegisteredPacket> packets;

    public SimplePacketRegistry() {
        this.packets = new ConcurrentHashMap<>();
    }

    public SimplePacketRegistry(final int size) {
        this.packets = new ConcurrentHashMap<>(size);
    }

    @SafeVarargs
    public SimplePacketRegistry(Class<? extends Packet>... packets) throws PacketRegistrationException {
        this(packets.length);
        registerPackets(packets);
    }

    public SimplePacketRegistry(Map<Integer, Class<? extends Packet>> packets) throws PacketRegistrationException {
        this(packets.size());
        registerPackets(packets);
    }

    @Override
    public void registerPacket(int packetId, Class<? extends Packet> packet) throws PacketRegistrationException {
        if (packetId == -1) throw new PacketRegistrationException("PacketID cannot be -1");
        if (containsPacketId(packetId)) throw new PacketRegistrationException("PacketID is already in use");

        try {
            RegisteredPacket registeredPacket = new RegisteredPacket(packet);
            this.packets.put(packetId, registeredPacket);
        } catch (NoSuchMethodException e) {
            throw new PacketRegistrationException("Failed to register packet", e);
        }
    }

    @Override
    public int getPacketId(Class<? extends Packet> packetClass) {
        return packets.entrySet().stream().filter(entry -> entry.getValue().getPacketClass().equals(packetClass)).findFirst().map(Map.Entry::getKey).orElse(-1);
    }

    @Override
    public <T extends Packet> T constructPacket(int packetId, long timestamp, byte[] data) throws InvocationTargetException, InstantiationException, IllegalAccessException {
        if (!containsPacketId(packetId)) throw new IllegalArgumentException("Packet " + packetId + " not found");
        return packets.get(packetId).create(packetId, timestamp, data);
    }

    @Override
    public boolean containsPacketId(int id) {
        return packets.containsKey(id);
    }

    @Override
    public Set<Class<? extends Packet>> packets() {
        return packets.values().stream().map(RegisteredPacket::getPacketClass).collect(Collectors.toSet());
    }
}