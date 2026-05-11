package dev.sweety.feature.service.impl;

import dev.sweety.feature.service.api.Service;
import dev.sweety.feature.service.api.ServiceRegistry;
import dev.sweety.feature.service.api.annotation.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DependencyInjectorTest {

    private ServiceRegistry registry;
    private DependencyInjector injector;

    @BeforeEach
    void setUp() {
        registry = new ServiceManager();
        injector = new DependencyInjector(registry);
    }

    @Test
    void testConstructorInjection() {
        registry.put(DatabaseService.class, new DatabaseService());
        
        AppService app = injector.instantiate(AppService.class);
        assertNotNull(app);
        assertNotNull(app.db());
    }

    @Test
    void testFieldInjection() throws Exception {
        DatabaseService db = new DatabaseService();
        registry.put(DatabaseService.class, db);

        FieldInjectionService service = new FieldInjectionService();
        injector.injectFields(service);
        
        assertEquals(db, service.db);
    }

    // Mock Services
    public static class DatabaseService implements Service {}

    public record AppService(DatabaseService db) implements Service {
        @Inject
        public AppService {
        }
        }

    public static class FieldInjectionService implements Service {
        @Inject
        public DatabaseService db;
    }
}
