package dev.sweety.netty.packet.registry;

import dev.sweety.netty.packet.model.Packet;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

public class RegisteredPacket {

    @FunctionalInterface
    interface ObtainFactory {
        Packet obtain(int id, long timestamp, byte[] data) throws Throwable;
    }

    private final Class<? extends Packet> packetClass;
    private @NotNull final ObtainFactory factory;

    public RegisteredPacket(Class<? extends Packet> packetClass) throws NoSuchMethodException {
        this.packetClass = packetClass;

        List<Constructor<?>> constructors = Arrays.stream(packetClass.getConstructors())
                .filter(constructor -> constructor.getParameterCount() == 3 &&
                        constructor.getParameterTypes()[0].equals(int.class) &&
                        constructor.getParameterTypes()[1].equals(long.class) &&
                        constructor.getParameterTypes()[2].equals(byte[].class))
                .toList();
        if (constructors.isEmpty())
            throw new NoSuchMethodException("Packet " + packetClass.getSimpleName() + " is missing (int, long, byte[]) constructor");

        // noinspection unchecked
        Constructor<? extends Packet> constructor = (Constructor<? extends Packet>) constructors.getFirst();

        // Auto-detect static obtain(int, long, byte[]) pool factory via MethodHandle (zero reflection overhead at call site)
        ObtainFactory detectedFactory = constructor::newInstance;
        try {
            Method m = packetClass.getMethod("obtain", int.class, long.class, byte[].class);
            if (Modifier.isStatic(m.getModifiers())) {
                MethodHandle mh = MethodHandles.lookup().unreflect(m);
                detectedFactory = (id, ts, data) -> (Packet) mh.invoke(id, ts, data);
            }
        } catch (NoSuchMethodException ignored) {
            throw new NoSuchMethodException("Packet " + packetClass.getSimpleName() + " is missing static obtain(int, long, byte[]) factory method");
        } catch (IllegalAccessException ignored) {
            // no pool factory on this packet type
        }
        this.factory = detectedFactory;
    }

    public <T extends Packet> T create(int id, long timestamp, byte[] data) throws InvocationTargetException, InstantiationException, IllegalAccessException {
        try {
            //noinspection unchecked
            return (T) this.factory.obtain(id, timestamp, data);
        } catch (Throwable t) {
            if (t instanceof RuntimeException re) throw re;
            throw new RuntimeException(t);
        }
    }

    public Class<? extends Packet> packetClass() {
        return packetClass;
    }
}
