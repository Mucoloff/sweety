package dev.sweety.feature.service.impl;

import dev.sweety.feature.service.api.Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServiceManagerTest {

    private ServiceManager manager;

    @BeforeEach
    void setUp() {
        manager = new ServiceManager();
    }

    @Test
    void testRegisterByClass() {
        manager.registerByClass(MockService.class);
        MockService service = manager.get(MockService.class);
        
        assertNotNull(service);
        assertTrue(service.enabled);
    }

    @Test
    void testLifecycle() {
        MockService service = new MockService();
        manager.put(MockService.class, service);
        
        assertTrue(service.enabled);
        
        manager.remove(MockService.class);
        assertFalse(service.enabled);
    }

    public static class MockService implements Service {
        boolean enabled = false;

        @Override
        public void onEnable() {
            enabled = true;
        }

        @Override
        public void onDisable() {
            enabled = false;
        }
    }
}
