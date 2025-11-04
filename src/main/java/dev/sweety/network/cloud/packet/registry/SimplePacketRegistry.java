package dev.sweety.network.cloud.packet.registry;


import dev.sweety.network.cloud.messaging.exception.PacketRegistrationException;
import dev.sweety.network.cloud.packet.model.Packet;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SimplePacketRegistry implements IPacketRegistry {

    private final Map<Byte, RegisteredPacket> packets = new ConcurrentHashMap<>();

    @Override
    public void registerPacket(byte packetId, Class<? extends Packet> packet) throws PacketRegistrationException {
        if (containsPacketId(packetId)) throw new PacketRegistrationException("PacketID is already in use");

        try {
            RegisteredPacket registeredPacket = new RegisteredPacket(packet);
            this.packets.put(packetId, registeredPacket);
        } catch (NoSuchMethodException e) {
            throw new PacketRegistrationException("Failed to register packet", e);
        }
    }

    @Override
    public byte getPacketId(Class<? extends Packet> packetClass) {
        return packets.entrySet().stream().filter(entry -> entry.getValue().getPacketClass().equals(packetClass)).findFirst().map(Map.Entry::getKey).orElse((byte) -1);
    }

    @Override
    public <T extends Packet> T constructPacket(byte packetId, long timestamp, byte[] data) throws InvocationTargetException, InstantiationException, IllegalAccessException {
        if (!containsPacketId(packetId)) throw new IllegalArgumentException("Packet " + packetId + " not found");
        return packets.get(packetId).create(packetId,timestamp, data);
    }

    @Override
    public boolean containsPacketId(byte id) {
        return packets.containsKey(id);
    }

}