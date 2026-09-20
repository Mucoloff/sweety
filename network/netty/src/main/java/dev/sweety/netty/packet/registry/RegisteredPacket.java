package dev.sweety.netty.packet.registry;

import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.model.Packet;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class RegisteredPacket {

    private final Class<? extends Packet> packetClass;
    private final Constructor<? extends Packet> noArgCtor;

    public RegisteredPacket(Class<? extends Packet> packetClass) throws NoSuchMethodException {
        this.packetClass = packetClass;
        try {
            this.noArgCtor = packetClass.getDeclaredConstructor();
            this.noArgCtor.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new NoSuchMethodException("Packet " + packetClass.getSimpleName() + " is missing a no-arg constructor");
        }
    }

    public <T extends Packet> T create(long timestamp, PacketBuffer buf)
            throws InvocationTargetException, InstantiationException, IllegalAccessException {
        try {
            //noinspection unchecked
            final T packet = (T) noArgCtor.newInstance();
            packet.assignTimestamp(timestamp);
            packet.read(buf);
            return packet;
        } catch (Throwable t) {
            throw t instanceof RuntimeException r ? r : new RuntimeException(t);
        }
    }

    public <T extends Packet> T create(long timestamp, byte[] data)
            throws InvocationTargetException, InstantiationException, IllegalAccessException {
        final PacketBuffer buf = new PacketBuffer(data);
        try {
            return create(timestamp, buf);
        } finally {
            buf.release();
        }
    }

    public Class<? extends Packet> packetClass() {
        return packetClass;
    }
}
