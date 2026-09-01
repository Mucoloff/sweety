package dev.sweety.service.impl;

import dev.sweety.service.DependsOn;
import dev.sweety.service.HealthCheck;
import dev.sweety.service.HealthStatus;
import dev.sweety.service.Service;
import dev.sweety.service.Start;
import dev.sweety.service.Stop;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ServiceContainerTest {

    private static final List<String> LIFECYCLE_LOG = new ArrayList<>();

    public static class DatabaseService implements Service, HealthCheck {
        @Start
        public void init() { LIFECYCLE_LOG.add("DB_START"); }

        @Stop
        public void shutdown() { LIFECYCLE_LOG.add("DB_STOP"); }

        @Override
        public HealthStatus checkHealth() { return HealthStatus.HEALTHY; }
    }

    @DependsOn(DatabaseService.class)
    public static class NetworkServer implements Service {
        @Start
        public void startServer() { LIFECYCLE_LOG.add("NET_START"); }

        @Stop
        public void stopServer() { LIFECYCLE_LOG.add("NET_STOP"); }
    }

    @Test
    public void testTopologicalLifecycleAndHealth() {
        LIFECYCLE_LOG.clear();

        ServiceContainerImpl container = new ServiceContainerImpl();
        DatabaseService db = new DatabaseService();
        NetworkServer net = new NetworkServer();

        // Register in reverse order to test dependency sorting
        container.register(NetworkServer.class, net);
        container.register(DatabaseService.class, db);

        container.startAll();

        // Must start Database before NetworkServer due to @DependsOn
        assertEquals(List.of("DB_START", "NET_START"), LIFECYCLE_LOG);

        // Check health
        Map<String, HealthStatus> health = container.checkHealth();
        assertEquals(HealthStatus.HEALTHY, health.get("DatabaseService"));

        container.stopAll();

        // Must stop NetworkServer before Database
        assertEquals(List.of("DB_START", "NET_START", "NET_STOP", "DB_STOP"), LIFECYCLE_LOG);
    }
}
