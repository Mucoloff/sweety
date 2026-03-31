package dev.sweety.saas.service;

import dev.sweety.data.ChecksumUtils;
import dev.sweety.data.HasId;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.buffer.io.Encoder;
import dev.sweety.netty.packet.buffer.io.callable.CallableDecoder;

import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ServiceType implements HasId, Encoder {
    private static final long seed = 0x9E3779B97F4A7C15L;
    private static final Map<Integer, ServiceType> ID = new HashMap<>();
    private static final Set<ServiceType> BASE = new HashSet<>();



    public static final ServiceType NONE = new ServiceType();

    private final int id;
    private final String name;

    private ServiceType() {
        this.id = -1;
        this.name = "none";
        ID.put(id, this);
    }

    private ServiceType(String name) {
        this.name = name;
        this.id = ChecksumUtils.crc32Int(name.getBytes(StandardCharsets.UTF_8), seed);
    }

    public static ServiceType of(int id) {
        return ID.getOrDefault(id, NONE);
    }

    public static ServiceType of(String name) {
        if (name == null) return NONE;
        int id = ChecksumUtils.crc32Int(name.getBytes(StandardCharsets.UTF_8), seed);
        return ID.compute(id, (k, v) -> {
            if (v == null) return new ServiceType(name);
            if (v == NONE) return v;
            if (v.id != id) throw new IllegalArgumentException("Hash collision for service type id: " + id);
            if (!v.name.equals(name))
                throw new IllegalArgumentException("Hash collision for service type name: " + name);
            return v;
        });
    }

    public void required() {
        BASE.add(this);
    }

    public static boolean isRequired(ServiceType type) {
        return BASE.contains(type);
    }

    public static Collection<ServiceType> values() {
        return ID.values();
    }

    public static Collection<ServiceType> requiredValues() {
        return Collections.unmodifiableSet(BASE);
    }

    @Override
    public void write(PacketBuffer buffer) {
        buffer.writeVarInt(this.id);
    }

    public static final CallableDecoder<ServiceType> DECODER = buffer -> of(buffer.readVarInt());

    public int id() {
        return this.id;
    }

    public String name() {
        return this.name;
    }
}
