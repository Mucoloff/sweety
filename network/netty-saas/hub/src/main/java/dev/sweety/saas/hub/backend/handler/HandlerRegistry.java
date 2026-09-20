package dev.sweety.saas.hub.backend.handler;

import dev.sweety.saas.hub.backend.ServiceNode;
import dev.sweety.saas.service.ServiceType;
import dev.sweety.util.logger.SimpleLogger;
import dev.sweety.util.logger.level.LogLevel;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

public class HandlerRegistry {

    // singleton justified: global handler map for hub pipeline wiring
    private static final SimpleLogger LOG = SimpleLogger.of(HandlerRegistry.class);
    private static final HandlerRegistry INSTANCE = new HandlerRegistry();

    public static HandlerRegistry getInstance() {
        return INSTANCE;
    }

    private HandlerRegistry() {
    }

    private static final ServiceNodeHandler NOOP_HANDLER = new ServiceNodeHandler(null) {
        @Override
        public void handle(io.netty.channel.ChannelHandlerContext ctx, dev.sweety.netty.packet.model.Packet packet) {}

        @Override
        public boolean handled(dev.sweety.netty.packet.model.Packet packet) {
            return false;
        }
    };

    private final Map<ServiceType, java.util.function.Function<ServiceNode, ? extends ServiceNodeHandler>> factories = new HashMap<>();

    public <T extends ServiceNodeHandler> void register(ServiceType type, java.util.function.Function<ServiceNode, T> factory) {
        factories.put(type, factory);
    }

    public <T extends ServiceNodeHandler> void register(ServiceType type, Class<T> clazz) {
        factories.put(type, node -> {
            try {
                return clazz.getDeclaredConstructor(ServiceNode.class).newInstance(node);
            } catch (Exception e) {
                throw new RuntimeException("Failed to instantiate handler for " + type, e);
            }
        });
    }

    public <T extends ServiceNodeHandler> T create(ServiceNode node) {
        final ServiceType type = node.type();
        @SuppressWarnings("unchecked")
        final java.util.function.Function<ServiceNode, T> factory = (java.util.function.Function<ServiceNode, T>) factories.get(type);
        if (factory == null) {
            @SuppressWarnings("unchecked")
            T noop = (T) NOOP_HANDLER;
            return noop;
        }
        return factory.apply(node);
    }

}
