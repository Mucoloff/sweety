package dev.sweety.netty.routing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class VirtualShardRouterTest {

    record QueryMessage(long accountId, String query) {}

    @Test
    public void testDeterministicShardRouting() {
        VirtualShardRouterAdapter<QueryMessage, String> router =
                new VirtualShardRouterAdapter<>(QueryMessage::accountId);

        assertEquals(8192, router.totalShards());

        List<String> nodes = List.of("db-node-0", "db-node-1", "db-node-2", "db-node-3");

        QueryMessage msg1 = new QueryMessage(1001L, "SELECT * FROM users");
        QueryMessage msg2 = new QueryMessage(1001L, "UPDATE users SET name='test'");
        QueryMessage msg3 = new QueryMessage(2048L, "SELECT * FROM orders");

        String node1 = router.route(msg1, nodes);
        String node2 = router.route(msg2, nodes);
        String node3 = router.route(msg3, nodes);

        assertNotNull(node1);
        // Same accountId key must route deterministically to the exact same node
        assertEquals(node1, node2);

        // Verify slot resolution
        int slot1 = router.resolveSlot(1001L);
        assertTrue(slot1 >= 0 && slot1 < 8192);
        int slot3 = router.resolveSlot(2048L);
        assertTrue(slot3 >= 0 && slot3 < 8192);

        // String partition key hashing
        int strSlot = router.resolveSlot("user-tenant-xyz");
        assertTrue(strSlot >= 0 && strSlot < 8192);
    }

    @Test
    public void testSingleTarget() {
        VirtualShardRouterAdapter<QueryMessage, String> router =
                new VirtualShardRouterAdapter<>(QueryMessage::accountId);

        List<String> single = List.of("solo-db");
        assertEquals("solo-db", router.route(new QueryMessage(12345L, "q"), single));
        assertNull(router.route(new QueryMessage(12345L, "q"), List.of()));
    }
}
