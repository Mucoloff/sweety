package dev.sweety.feature.service.impl;

import dev.sweety.feature.service.api.Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;

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

    @Test
    void testReplaceServiceDisablesOldBeforeEnablingNew() {
        List<String> log = new ArrayList<>();
        MockService first = new MockService(log, "a");
        MockService second = new MockService(log, "b");
        manager.put(MockService.class, first);
        log.clear();
        manager.put(MockService.class, second);
        assertEquals(List.of("disable:a", "enable:b"), log);
        assertTrue(second.enabled);
        assertFalse(first.enabled);
    }

    @Test
    void testErrorPaths() {
        assertThrows(NullPointerException.class, () -> manager.get((Class<?>) null));
        assertThrows(NullPointerException.class, () -> manager.put((Class<MockService>) null, new MockService()));
        assertThrows(NullPointerException.class, () -> manager.put(MockService.class, (MockService) null));
        assertThrows(NullPointerException.class, () -> manager.registerByClass(null));
    }

    public static class MockService implements Service {
        boolean enabled = false;
        private final List<String> eventLog;
        private final String id;

        public MockService() {
            this(null, null);
        }

        public MockService(List<String> eventLog, String id) {
            this.eventLog = eventLog;
            this.id = id;
        }

        @Override
        public void onEnable() {
            enabled = true;
            if (eventLog != null) eventLog.add("enable:" + id);
        }

        @Override
        public void onDisable() {
            enabled = false;
            if (eventLog != null) eventLog.add("disable:" + id);
        }
    }
}
