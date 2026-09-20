package dev.sweety.saas.service;

import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.data.ChecksumUtils;
import dev.sweety.data.HasId;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.buffer.io.Encoder;
import dev.sweety.netty.packet.buffer.io.callable.CallableDecoder;

import dev.sweety.math.list.Int2ObjectConcurrentOpenHashMap;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ServiceType implements HasId, Encoder {
    private static final long seed = 0x9E3779B97F4A7C15L;
    private static final Int2ObjectConcurrentOpenHashMap<ServiceType> ID = Int2ObjectConcurrentOpenHashMap.create();
    private static final Set<ServiceType> BASE = ConcurrentHashMap.newKeySet();

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
        ServiceType type = ID.get(id);
        return type != null ? type : NONE;
    }

    public static ServiceType of(String name) {
        if (name == null) return NONE;
        int id = ChecksumUtils.crc32Int(name.getBytes(StandardCharsets.UTF_8), seed);
        ServiceType existing = ID.get(id);
        if (existing != null) {
            if (existing != NONE && !existing.name.equals(name)) {
                throw new IllegalArgumentException("Hash collision for service type name: " + name);
            }
            return existing;
        }
        return ID.computeIfAbsent(id, k -> new ServiceType(name));
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
    public void write(BufferWriter buffer) {
        buffer.writeVarInt(this.id);
    }

    public static final CallableDecoder<ServiceType> DECODER = buffer -> of(buffer.readVarInt());

    public int id() {
        return this.id;
    }

    public String name() {
        return this.name;
    }

    @Override
    public String toString() {
        return this.name();
    }
}
