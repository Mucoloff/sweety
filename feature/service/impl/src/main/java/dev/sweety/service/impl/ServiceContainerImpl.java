package dev.sweety.service.impl;

import dev.sweety.service.HealthCheck;
import dev.sweety.service.HealthStatus;
import dev.sweety.service.Service;
import dev.sweety.service.ServiceContainer;
import dev.sweety.service.Start;
import dev.sweety.service.Stop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ServiceContainerImpl implements ServiceContainer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceContainerImpl.class);

    private final Map<Class<?>, Object> services = new LinkedHashMap<>();
    private final Map<String, HealthCheck> healthChecks = new ConcurrentHashMap<>();
    private volatile boolean running = false;

    @Override
    public synchronized <T> ServiceContainer register(Class<T> type, T instance) {
        services.put(type, instance);
        if (instance instanceof HealthCheck hc) {
            healthChecks.put(type.getSimpleName(), hc);
        }
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(Class<T> type) {
        return Optional.ofNullable((T) services.get(type));
    }

    @Override
    public <T> T require(Class<T> type) {
        return get(type).orElseThrow(() -> new NoSuchElementException("Service not found: " + type.getName()));
    }

    @Override
    public synchronized void startAll() {
        if (running) return;
        List<Class<?>> startOrder = DependencyGraph.computeStartOrder(services);
        LOGGER.info("Starting {} services in topological order...", startOrder.size());

        for (Class<?> clazz : startOrder) {
            Object instance = services.get(clazz);
            invokeLifecycleMethods(instance, Start.class);
            LOGGER.debug("Service started: {}", clazz.getSimpleName());
        }
        running = true;
    }

    @Override
    public synchronized void stopAll() {
        if (!running) return;
        List<Class<?>> startOrder = DependencyGraph.computeStartOrder(services);
        List<Class<?>> stopOrder = new java.util.ArrayList<>(startOrder);
        Collections.reverse(stopOrder);

        LOGGER.info("Stopping {} services in reverse topological order...", stopOrder.size());
        for (Class<?> clazz : stopOrder) {
            Object instance = services.get(clazz);
            invokeLifecycleMethods(instance, Stop.class);
            LOGGER.debug("Service stopped: {}", clazz.getSimpleName());
        }
        running = false;
    }

    @Override
    public Map<String, HealthStatus> checkHealth() {
        Map<String, HealthStatus> statuses = new LinkedHashMap<>();
        for (Map.Entry<String, HealthCheck> entry : healthChecks.entrySet()) {
            try {
                statuses.put(entry.getKey(), entry.getValue().checkHealth());
            } catch (Exception e) {
                statuses.put(entry.getKey(), HealthStatus.UNHEALTHY);
            }
        }
        return statuses;
    }

    private void invokeLifecycleMethods(Object instance, Class<? extends java.lang.annotation.Annotation> annotationClass) {
        if (instance == null) return;
        for (Method method : instance.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(annotationClass)) {
                try {
                    method.setAccessible(true);
                    method.invoke(instance);
                } catch (Exception e) {
                    LOGGER.error("Failed to invoke {} on {}", annotationClass.getSimpleName(), instance.getClass().getName(), e);
                }
            }
        }
    }
}
