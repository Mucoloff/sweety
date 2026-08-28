package dev.sweety.sql4j.api;

import dev.sweety.sql4j.api.shard.VirtualShardRouter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualShardRouterTest {

    @Test
    void testUniformDistributionAndRemap() {
        int nodeCount = 4;
        VirtualShardRouter router = VirtualShardRouter.createDefault(nodeCount); // 8192 shards

        int[] hitsPerNode = new int[nodeCount];
        int sampleCount = 100_000;

        for (long userId = 1; userId <= sampleCount; userId++) {
            int node = router.resolveNode(userId);
            assertTrue(node >= 0 && node < nodeCount);
            hitsPerNode[node]++;
        }

        // Each node should receive approximately 25% of keys (+/- 3%)
        for (int i = 0; i < nodeCount; i++) {
            double fraction = (double) hitsPerNode[i] / sampleCount;
            assertTrue(fraction >= 0.20 && fraction <= 0.30, "Node " + i + " fraction was " + fraction);
        }

        // Test dynamic remap of shard 42
        int originalNode = router.getNodeForShard(42);
        int targetNode = (originalNode + 1) % nodeCount;
        router.remapShard(42, targetNode);
        assertEquals(targetNode, router.getNodeForShard(42));
    }
}
