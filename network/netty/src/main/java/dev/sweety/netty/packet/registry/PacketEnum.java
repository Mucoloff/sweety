package dev.sweety.netty.packet.registry;

import dev.sweety.logger.level.LogLevel;
import dev.sweety.logger.SimpleLogger;
import dev.sweety.netty.messaging.exception.PacketRegistrationException;
import dev.sweety.netty.packet.model.Packet;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public interface PacketEnum {

    Map<Class<?>, Int2ObjectMap<PacketEnum>> LOOKUP = new HashMap<>();

    int id();

    String name();

    Class<? extends Packet> packetClass();

    default void log(LogLevel level, Object... message) {
        SimpleLogger.log(level, getClass().getSimpleName().replace("Packets", ""), message);
    }

    List<PacketEnum> UNREGISTERED = new LinkedList<>();

    default void add() {
        UNREGISTERED.add(this);
    }

    default void register(IPacketRegistry registry, PacketEnum[]... arrays) {
        for (PacketEnum[] array : arrays) {
            if (array.length == 0) continue;

            Class<?> enumClass = array.getClass().getComponentType();
            Int2ObjectMap<PacketEnum> map = LOOKUP.computeIfAbsent(enumClass, k -> new Int2ObjectOpenHashMap<>());

            for (PacketEnum packetEnum : array) {
                map.put(packetEnum.id(), packetEnum);
                if (packetEnum.id() == -1 || packetEnum.packetClass() == null) continue;
                try {
                    registry.registerPacket(packetEnum.id(), packetEnum.packetClass());
                } catch (PacketRegistrationException e) {
                    log(LogLevel.ERROR, "Failed to register packet %s:".formatted(packetEnum.name()), e);
                }
            }
        }
    }

    default void flag() {
        if (UNREGISTERED.isEmpty()) return;
        log(LogLevel.WARN, "packets with no class implementation:\n", UNREGISTERED.stream().map(packet -> "%s(%s)".formatted(packet.name(), packet.id())));
    }

    static <T extends Enum<T> & PacketEnum> T getById(Class<T> enumClass, int id, T defaultVal) {
        final Int2ObjectMap<PacketEnum> map = LOOKUP.get(enumClass);
        if (map == null) return defaultVal;

        //noinspection unchecked
        final T val = (T) map.get(id);
        return val != null ? val : defaultVal;
    }
}


