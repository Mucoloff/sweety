package dev.sweety.netty.packet.registry;

import dev.sweety.netty.messaging.exception.PacketRegistrationException;
import dev.sweety.netty.packet.model.Packet;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Set;

public interface IPacketRegistry {

    default void registerPacket(int packetId, Packet packet) throws PacketRegistrationException {
        registerPacket(packetId, packet.getClass());
    }

    void registerPacket(int packetId, Class<? extends Packet> packet) throws PacketRegistrationException;

    int getPacketId(Class<? extends Packet> packetClass);

    <T extends Packet> T constructPacket(int packetId, long timestamp, byte[] data) throws InvocationTargetException, InstantiationException, IllegalAccessException;

    boolean containsPacketId(int id);

    default void registerPackets(Map<Integer, Class<? extends Packet>> packets) throws PacketRegistrationException{
        for (Map.Entry<Integer, Class<? extends Packet>> entry : packets.entrySet())
            registerPacket(entry.getKey(), entry.getValue());
    }

    default void registerPackets(Class<? extends Packet>... packets) throws PacketRegistrationException {
        for (int id = 0; id < packets.length; id++) registerPacket(id, packets[id]);
    }

    Set<Class<? extends Packet>> packets();

}