package dev.sweety.network.cloud.packet.registry;

import dev.sweety.network.cloud.messaging.exception.PacketRegistrationException;
import dev.sweety.network.cloud.packet.model.Packet;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

public interface IPacketRegistry {

    default void registerPacket(short packetId, Packet packet) throws PacketRegistrationException {
        registerPacket(packetId, packet.getClass());
    }

    void registerPacket(short packetId, Class<? extends Packet> packet) throws PacketRegistrationException;

    short getPacketId(Class<? extends Packet> packetClass);

    <T extends Packet> T constructPacket(short packetId, long timestamp, byte[] data) throws InvocationTargetException, InstantiationException, IllegalAccessException;

    boolean containsPacketId(short id);

    default void registerPackets(Map<Short, Class<? extends Packet>> packets) throws PacketRegistrationException{
        for (Map.Entry<Short, Class<? extends Packet>> entry : packets.entrySet())
            registerPacket(entry.getKey(), entry.getValue());
    }

    default void registerPackets(Class<? extends Packet>... packets) throws PacketRegistrationException {
        for (short id = 0; id < packets.length; id++) registerPacket(id, packets[id]);
    }

}