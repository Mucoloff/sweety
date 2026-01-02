package dev.sweety.core.service.impl;

import java.util.List;
import java.util.stream.Collectors;

public final class ServiceContext<T> {

    private final ServiceManager manager;
    private final Class<T> baseType;

    public ServiceContext(ServiceManager manager, Class<T> baseType) {
        this.manager = manager;
        this.baseType = baseType;
    }

    public <S extends T> void install(Class<S> type, S service) {
        manager.put(type, service);
    }

    public <S extends T> S get(Class<S> type) {
        return manager.get(type);
    }

    public T get() {
        return manager.get(baseType);
    }

    public List<T> all() {
        return manager.entrySet().stream().filter(baseType::isInstance).map(baseType::cast).collect(Collectors.toList());
    }
}
