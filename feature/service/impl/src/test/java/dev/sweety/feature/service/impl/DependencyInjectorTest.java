package dev.sweety.feature.service.impl;

import dev.sweety.feature.service.api.Provider;
import dev.sweety.feature.service.api.Service;
import dev.sweety.feature.service.api.ServiceRegistry;
import dev.sweety.feature.service.api.annotation.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DependencyInjectorTest {

    private ServiceRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ServiceManager();
    }

    @Test
    void testConstructorInjection() {
        registry.put(DatabaseService.class, new DatabaseService());
        
        AppService app = DependencyInjector.instantiate(registry, AppService.class);
        assertNotNull(app);
        assertNotNull(app.db());
    }

    @Test
    void testFieldInjection() throws Exception {
        DatabaseService db = new DatabaseService();
        registry.put(DatabaseService.class, db);

        FieldInjectionService service = new FieldInjectionService();
        DependencyInjector.injectFields(registry, service);
        
        assertEquals(db, service.db);
    }

    @Test
    void testDefaultConstructor() {
        DefaultConstructorService service = DependencyInjector.instantiate(registry, DefaultConstructorService.class);
        assertNotNull(service);
        assertTrue(service.initialized);
    }

    @Test
    void testSingleConstructorWithoutAnnotation() {
        registry.put(DatabaseService.class, new DatabaseService());
        SingleConstructorService service = DependencyInjector.instantiate(registry, SingleConstructorService.class);
        assertNotNull(service);
        assertNotNull(service.db);
    }

    @Test
    void testMultipleConstructorsWithoutInjectThrows() {
        assertThrows(RuntimeException.class, () -> DependencyInjector.instantiate(registry, MultipleConstructorsService.class));
    }

    @Test
    void testInheritedFieldInjection() {
        DatabaseService db = new DatabaseService();
        registry.put(DatabaseService.class, db);

        InheritedService service = DependencyInjector.instantiate(registry, InheritedService.class);
        assertNotNull(service.db);
        assertEquals(db, service.db);
    }

    @Test
    void testPrivateFieldAndConstructorInjection() {
        registry.put(DatabaseService.class, new DatabaseService());
        PrivateService service = DependencyInjector.instantiate(registry, PrivateService.class);
        assertNotNull(service);
        assertNotNull(service.getDb());
    }

    @Test
    void testMissingDependencyThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> DependencyInjector.instantiate(registry, AppService.class));
        assertTrue(ex.getMessage().contains("constructor parameter"));
        assertTrue(ex.getMessage().contains("DatabaseService"));
    }

    @Test
    void testMissingFieldDependencyThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> DependencyInjector.instantiate(registry, FieldInjectionService.class));
        assertTrue(ex.getMessage().contains("field"));
        assertTrue(ex.getMessage().contains("db"));
    }

    @Test
    void testCircularDependencyThrows() {
        registry.put(CircB.class, (Provider<CircB>) () -> DependencyInjector.instantiate(registry, CircB.class));
        registry.put(CircA.class, (Provider<CircA>) () -> DependencyInjector.instantiate(registry, CircA.class));
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> DependencyInjector.instantiate(registry, CircA.class));
        assertTrue(ex.getMessage().contains("Circular dependency"));
    }

    @Test
    void testInterfaceInstantiationThrows() {
        assertThrows(RuntimeException.class, () -> DependencyInjector.instantiate(registry, Service.class));
    }

    @Test
    void testNullArguments() {
        assertThrows(NullPointerException.class, () -> DependencyInjector.instantiate(null, AppService.class));
        assertThrows(NullPointerException.class, () -> DependencyInjector.instantiate(registry, null));
    }

    public static class CircA implements Service {
        @Inject
        public CircA(CircB b) {}
    }

    public static class CircB implements Service {
        @Inject
        public CircB(CircA a) {}
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

    public static class DefaultConstructorService implements Service {
        public boolean initialized = false;
        public DefaultConstructorService() {
            this.initialized = true;
        }
    }

    public record SingleConstructorService(DatabaseService db) implements Service {
    }

    public static class MultipleConstructorsService implements Service {
        public MultipleConstructorsService(DatabaseService db) {}
        public MultipleConstructorsService(String other) {}
    }

    public static class BaseService implements Service {
        @Inject
        public DatabaseService db;
    }

    public static class InheritedService extends BaseService {}

    public static class PrivateService implements Service {
        @Inject
        private DatabaseService db;

        private PrivateService() {}

        public DatabaseService getDb() {
            return db;
        }
    }
}
