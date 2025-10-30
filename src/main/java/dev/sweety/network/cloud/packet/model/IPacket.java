package dev.sweety.network.cloud.packet.model;


import dev.sweety.network.cloud.packet.buffer.PacketBuffer;

public interface IPacket {
    byte[] getData();

    byte getId();

    Long getTimestamp();

    PacketBuffer getBuffer();
}
