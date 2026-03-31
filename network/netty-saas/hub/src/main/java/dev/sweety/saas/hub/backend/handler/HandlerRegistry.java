package dev.sweety.saas.hub.backend.handler;

import dev.sweety.saas.hub.backend.ServiceNode;
import dev.sweety.saas.service.ServiceType;
import dev.sweety.util.logger.SimpleLogger;
import dev.sweety.util.logger.level.LogLevel;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

public class HandlerRegistry {

    public static final HandlerRegistry INSTANCE = new HandlerRegistry();

    HandlerRegistry() {
    }

    private final Map<ServiceType, Class<? extends ServiceNodeHandler>> handlers = new HashMap<>();

    public <T extends ServiceNodeHandler> void register(ServiceType type, Class<T> clazz) {
        handlers.put(type, clazz);
    }

    public <T extends ServiceNodeHandler> T create(ServiceNode node) {
        final ServiceType type = node.type();
        //noinspection unchecked
        final Class<T> clazz = (Class<T>) handlers.get(type);
        if (clazz == null) {
            SimpleLogger.log(LogLevel.WARN, "handler", "Handler not found for type " + type + " node " + node.getClass().getName(), "using an empty handler");
            //noinspection unchecked
            return (T) new EmptyHandler(node);
        }

        try {
            return clazz.getDeclaredConstructor(ServiceNode.class).newInstance(node);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                 NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

}
