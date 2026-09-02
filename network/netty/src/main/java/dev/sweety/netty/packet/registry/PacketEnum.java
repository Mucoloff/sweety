package dev.sweety.netty.packet.registry;

import dev.sweety.util.logger.level.LogLevel;
import dev.sweety.util.logger.LoggerFactory;
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

    Class<? extends Packet> packetClass();

    default void log(LogLevel level, Object... message) {
        LoggerFactory.getLogger(getClass().getSimpleName().replace("Packets", "")).log(level, message);
    }

    List<PacketEnum> UNREGISTERED = new LinkedList<>();

    default void add() {
        UNREGISTERED.add(this);
    }

    default void register(PacketRegistry registry, PacketEnum[]... arrays) {
        for (PacketEnum[] array : arrays) {
            if (array.length == 0) continue;

            Class<?> enumClass = array.getClass().getComponentType();
            Int2ObjectMap<PacketEnum> map = LOOKUP.computeIfAbsent(enumClass, k -> {
                Int2ObjectOpenHashMap<PacketEnum> m = new Int2ObjectOpenHashMap<>();
                m.defaultReturnValue(None.NONE);
                return m;
            });

            for (PacketEnum packetEnum : array) {
                map.put(packetEnum.id(), packetEnum);
                if (packetEnum.id() == -1 || packetEnum.packetClass() == null) continue;
                try {
                    registry.registerPacket(packetEnum.id(), packetEnum.packetClass());
                } catch (PacketRegistrationException e) {
                    log(LogLevel.ERROR, "Failed to register packet %s:".formatted(((Enum<?>) packetEnum).name()), e);
                }
            }
        }
    }

    static record None(int id, Class<? extends Packet> packetClass) implements PacketEnum {

        public static None NONE = new None(-1, null);

    }

    default void flag() {
        if (UNREGISTERED.isEmpty()) return;
        log(LogLevel.WARN, "packets with no class implementation:\n", UNREGISTERED.stream().map(packet -> "%s(%s)".formatted(((Enum<?>) packet).name(), packet.id())));
    }

    static <T extends Enum<T> & PacketEnum> T getById(Class<T> enumClass, int id, T defaultVal) {
        final Int2ObjectMap<PacketEnum> map = LOOKUP.get(enumClass);
        if (map == null) return defaultVal;

        final PacketEnum val = map.get(id);
        if (val == null || val == None.NONE || !enumClass.isInstance(val)) {
            return defaultVal;
        }
        return enumClass.cast(val);
    }
}


