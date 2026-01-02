package dev.sweety.netty.packet.registry;

import dev.sweety.netty.packet.model.Packet;
import lombok.Getter;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;

@Getter
public class RegisteredPacket {

    private final Class<? extends Packet> packetClass;
    private final Constructor<? extends Packet> constructor;

    public RegisteredPacket(Class<? extends Packet> packetClass) throws NoSuchMethodException {
        this.packetClass = packetClass;

        List<Constructor<?>> constructors = Arrays.stream(packetClass.getConstructors())
                .filter(constructor -> constructor.getParameterCount() == 3 &&
                        constructor.getParameterTypes()[0].equals(short.class) &&
                        constructor.getParameterTypes()[1].equals(long.class) &&
                        constructor.getParameterTypes()[2].equals(byte[].class))
                .toList();
        if (constructors.isEmpty())
            throw new NoSuchMethodException("Packet " + packetClass.getSimpleName() + " is missing (short, long, byte[]) constructor");

        // noinspection unchecked
        this.constructor = (Constructor<? extends Packet>) constructors.getFirst();
    }

    public <T extends Packet> T create(short id, long timestamp, byte[] data) throws InvocationTargetException, InstantiationException, IllegalAccessException {
        // noinspection unchecked
        return (T) this.constructor.newInstance(id, timestamp, data);
    }
}