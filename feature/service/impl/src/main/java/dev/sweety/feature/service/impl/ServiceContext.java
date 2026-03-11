package dev.sweety.feature.service.impl;

import java.util.List;
import java.util.stream.Collectors;

public final class ServiceContext<BaseType> {

    private final ServiceManager manager;
    private final Class<BaseType> baseType;

    public ServiceContext(ServiceManager manager, Class<BaseType> baseType) {
        this.manager = manager;
        this.baseType = baseType;
    }

    public <Service extends BaseType> void install(Class<Service> type, Service service) {
        manager.put(type, service);
    }

    public <S extends BaseType> S get(Class<S> type) {
        return manager.get(type);
    }

    public BaseType get() {
        return manager.get(baseType);
    }

    public List<BaseType> all() {
        return manager.entrySet().stream().filter(baseType::isInstance).map(baseType::cast).collect(Collectors.toList());
    }
}
