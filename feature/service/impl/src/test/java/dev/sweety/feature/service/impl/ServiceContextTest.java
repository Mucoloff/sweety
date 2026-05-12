package dev.sweety.feature.service.impl;

import dev.sweety.feature.service.api.Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceContextTest {

    private ServiceManager manager;
    private ServiceContext<BasePlugin> context;

    @BeforeEach
    void setUp() {
        manager = new ServiceManager();
        context = new ServiceContext<>(manager, BasePlugin.class);
    }

    @Test
    void allListsImplementationsOfBaseType() {
        ConcreteA a = new ConcreteA();
        ConcreteB b = new ConcreteB();
        manager.put(ConcreteA.class, a);
        manager.put(ConcreteB.class, b);
        manager.put(ServiceManagerTest.MockService.class, new ServiceManagerTest.MockService());

        List<BasePlugin> all = context.all();

        assertEquals(2, all.size());
        assertTrue(all.contains(a));
        assertTrue(all.contains(b));
    }

    interface BasePlugin extends Service {
    }

    static final class ConcreteA implements BasePlugin {
    }

    static final class ConcreteB implements BasePlugin {
    }
}
