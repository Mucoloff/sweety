package dev.sweety.netty.packet.registry;

import dev.sweety.util.logger.LoggerFactory;
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
    private final it.unimi.dsi.fastutil.ints.Int2ByteOpenHashMap idToTransport;

    public OptimizedPacketRegistry() {
        this.idToPacket = new Int2ObjectOpenHashMap<>();
        this.classToId = new Object2IntOpenHashMap<>();
        this.classToId.defaultReturnValue(-1);
        this.idToTransport = new it.unimi.dsi.fastutil.ints.Int2ByteOpenHashMap();
        this.idToTransport.defaultReturnValue(dev.sweety.netty.messaging.transport.TransportMode.FLAG_TCP);
    }

    public OptimizedPacketRegistry(final int size) {
        this.idToPacket = new Int2ObjectOpenHashMap<>(size);
        this.classToId = new Object2IntOpenHashMap<>(size);
        this.classToId.defaultReturnValue(-1);
        this.idToTransport = new it.unimi.dsi.fastutil.ints.Int2ByteOpenHashMap(size);
        this.idToTransport.defaultReturnValue(dev.sweety.netty.messaging.transport.TransportMode.FLAG_TCP);
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

            dev.sweety.netty.packet.annotation.TransportHint hint =
                    packet.getAnnotation(dev.sweety.netty.packet.annotation.TransportHint.class);
            byte mask = (hint != null) ? hint.value().mask() : dev.sweety.netty.messaging.transport.TransportMode.FLAG_TCP;
            idToTransport.put(packetId, mask);
        } catch (NoSuchMethodException e) {
            throw new PacketRegistrationException("Cannot register packet", e);
        }
    }

    /** Call after all registrations are done to compact both maps for cache-friendly reads. */
    public void trim() {
        idToPacket.trim();
        classToId.trim();
        idToTransport.trim();
    }

    @Override
    public int getPacketId(Class<? extends Packet> packetClass) {
        int id = classToId.getInt(packetClass);
        if (id == -1) LoggerFactory.getLogger("OptimizedPacketRegistry").debug("Packet {} not registered!", packetClass.getName());
        return id;
    }

    @Override
    public <T extends Packet> T constructPacket(int packetId, long timestamp, byte[] data)
            throws InvocationTargetException, InstantiationException, IllegalAccessException {
        RegisteredPacket registered = idToPacket.get(packetId);
        if (registered == null) throw new IllegalArgumentException("Unknown packet id " + packetId);
        return registered.create(timestamp, data);
    }

    @Override
    public boolean containsPacketId(int id) {
        return idToPacket.containsKey(id);
    }

    @Override
    public Set<Class<? extends Packet>> packets() {
        return classToId.keySet();
    }

    @Override
    public byte getTransportMode(int packetId) {
        return idToTransport.get(packetId);
    }

    @Override
    public byte getTransportMode(Class<? extends Packet> packetClass) {
        int id = getPacketId(packetClass);
        return id != -1 ? idToTransport.get(id) : dev.sweety.netty.messaging.transport.TransportMode.FLAG_TCP;
    }
}