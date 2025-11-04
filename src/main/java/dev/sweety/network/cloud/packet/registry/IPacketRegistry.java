package dev.sweety.network.cloud.packet.registry;

import dev.sweety.network.cloud.messaging.exception.PacketRegistrationException;
import dev.sweety.network.cloud.packet.model.Packet;

import java.lang.reflect.InvocationTargetException;

public interface IPacketRegistry {

    default void registerPacket(byte packetId, Packet packet) throws PacketRegistrationException {
        registerPacket(packetId, packet.getClass());
    }

    void registerPacket(byte packetId, Class<? extends Packet> packet) throws PacketRegistrationException;

    byte getPacketId(Class<? extends Packet> packetClass);

    <T extends Packet> T constructPacket(byte packetId, long timestamp, byte[] data) throws InvocationTargetException, InstantiationException, IllegalAccessException;

    boolean containsPacketId(byte id);

    default void registerPackets(Class<? extends Packet>... packets) throws PacketRegistrationException {
        for (byte id = 0; id < packets.length; id++) registerPacket(id, packets[id]);
    }

}