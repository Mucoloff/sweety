package dev.sweety.netty.packet.registry;

import dev.sweety.util.logger.level.LogLevel;
import dev.sweety.util.logger.SimpleLogger;
import dev.sweety.netty.messaging.exception.PacketRegistrationException;
import dev.sweety.netty.packet.model.Packet;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Set;

public class OptimizedPacketRegistry implements PacketRegistry {

    private final Int2ObjectOpenHashMap<RegisteredPacket> idToPacket;
    private final Object2IntOpenHashMap<Class<? extends Packet>> classToId;

    public OptimizedPacketRegistry() {
        this.idToPacket = new Int2ObjectOpenHashMap<>();
        this.classToId = new Object2IntOpenHashMap<>();
        this.classToId.defaultReturnValue(-1);
    }

    public OptimizedPacketRegistry(final int size) {
        this.idToPacket = new Int2ObjectOpenHashMap<>(size);
        this.classToId = new Object2IntOpenHashMap<>(size);
        this.classToId.defaultReturnValue(-1);
    }

    @SafeVarargs
    public OptimizedPacketRegistry(Class<? extends Packet>... packets) throws PacketRegistrationException {
        this(packets.length);
        registerPackets(packets);
        trim();
    }

    public OptimizedPacketRegistry(Map<Integer, Class<? extends Packet>> packets) throws PacketRegistrationException {
        this(packets.size());
        registerPackets(packets);
        trim();
    }

    @Override
    public void registerPacket(int packetId, Class<? extends Packet> packet) throws PacketRegistrationException {
        if (packetId == -1) throw new PacketRegistrationException("PacketID cannot be -1");

        try {
            RegisteredPacket registered = new RegisteredPacket(packet);
            if (idToPacket.putIfAbsent(packetId, registered) != null)
                throw new PacketRegistrationException("PacketID already in use");
            classToId.put(packet, packetId);
        } catch (NoSuchMethodException e) {
            throw new PacketRegistrationException("Cannot register packet", e);
        }
    }

    /** Call after all registrations are done to compact both maps for cache-friendly reads. */
    public void trim() {
        idToPacket.trim();
        classToId.trim();
    }

    @Override
    public int getPacketId(Class<? extends Packet> packetClass) {
        int id = classToId.getInt(packetClass);
        if (id == -1) SimpleLogger.log(LogLevel.DEBUG, "Packet " + packetClass.getName() + " not registered!");
        return id;
    }

    @Override
    public <T extends Packet> T constructPacket(int packetId, long timestamp, byte[] data)
            throws InvocationTargetException, InstantiationException, IllegalAccessException {
        RegisteredPacket registered = idToPacket.get(packetId);
        if (registered == null) throw new IllegalArgumentException("Unknown packet id " + packetId);
        return registered.create(packetId, timestamp, data);
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