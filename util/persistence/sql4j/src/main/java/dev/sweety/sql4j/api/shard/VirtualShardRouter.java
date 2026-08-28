package dev.sweety.sql4j.api.shard;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * Deterministic, O(1) Virtual Shard Router.
 *
 * <p>Partitions a key space into {@code totalShards} virtual slots (default 8192, a power of 2)
 * using bitwise masking: {@code virtualShard = hash(key) & (totalShards - 1)}.
 *
 * <p>The array {@code shardToNodeMap} maps each virtual shard to a physical database/node ID (0..N-1).
 * Live migrations update single slots atomically in the mapping array without redistributing other shards.
 */
public final class VirtualShardRouter {

    public static final int DEFAULT_SHARDS = 8192; // 2^13

    private final int totalShards;
    private final int mask;
    private final int[] shardToNodeMap;

    public VirtualShardRouter(int totalShards, int nodeCount) {
        if (totalShards <= 0 || (totalShards & (totalShards - 1)) != 0) {
            throw new IllegalArgumentException("totalShards must be a positive power of 2: " + totalShards);
        }
        if (nodeCount <= 0) {
            throw new IllegalArgumentException("nodeCount must be positive: " + nodeCount);
        }

        this.totalShards = totalShards;
        this.mask = totalShards - 1;
        this.shardToNodeMap = new int[totalShards];

        // Initial uniform distribution across nodes
        int shardsPerNode = totalShards / nodeCount;
        for (int i = 0; i < totalShards; i++) {
            this.shardToNodeMap[i] = Math.min(i / shardsPerNode, nodeCount - 1);
        }
    }

    public static VirtualShardRouter createDefault(int nodeCount) {
        return new VirtualShardRouter(DEFAULT_SHARDS, nodeCount);
    }

    /**
     * Resolves the virtual shard index (0..totalShards-1) for a 64-bit primitive key (e.g. userId).
     */
    public int resolveVirtualShard(long key) {
        int hash = hashLong(key);
        return hash & mask;
    }

    /**
     * Resolves the virtual shard index for a string key.
     */
    public int resolveVirtualShard(String key) {
        if (key == null) return 0;
        int hash = murmur3(key.getBytes(StandardCharsets.UTF_8));
        return hash & mask;
    }

    /**
     * Resolves the physical target node ID (0..nodeCount-1) for a primitive 64-bit key.
     */
    public int resolveNode(long key) {
        int virtualShard = resolveVirtualShard(key);
        return getNodeForShard(virtualShard);
    }

    /**
     * Resolves the physical target node ID for a string key.
     */
    public int resolveNode(String key) {
        int virtualShard = resolveVirtualShard(key);
        return getNodeForShard(virtualShard);
    }

    public synchronized int getNodeForShard(int virtualShard) {
        if (virtualShard < 0 || virtualShard >= totalShards) {
            throw new IndexOutOfBoundsException("virtualShard out of range: " + virtualShard);
        }
        return shardToNodeMap[virtualShard];
    }

    /**
     * Atomically remaps a virtual shard to a new physical node (used during live migration).
     */
    public synchronized void remapShard(int virtualShard, int newNodeId) {
        if (virtualShard < 0 || virtualShard >= totalShards) {
            throw new IndexOutOfBoundsException("virtualShard out of range: " + virtualShard);
        }
        shardToNodeMap[virtualShard] = newNodeId;
    }

    /**
     * Updates the entire routing table from an external state source (e.g. Redis Pub/Sub sync).
     */
    public synchronized void updateRoutingTable(int[] newMap) {
        Objects.requireNonNull(newMap, "newMap must not be null");
        if (newMap.length != totalShards) {
            throw new IllegalArgumentException("Routing table size mismatch: expected " + totalShards + ", got " + newMap.length);
        }
        System.arraycopy(newMap, 0, this.shardToNodeMap, 0, totalShards);
    }

    public synchronized int[] exportRoutingTable() {
        return Arrays.copyOf(shardToNodeMap, totalShards);
    }

    public int getTotalShards() {
        return totalShards;
    }

    private static int hashLong(long v) {
        v ^= (v >>> 33);
        v *= 0xff51afd7ed558ccdL;
        v ^= (v >>> 33);
        v *= 0xc4ceb9fe1a85ec53L;
        v ^= (v >>> 33);
        return (int) v;
    }

    private static int murmur3(byte[] data) {
        int h = 0x9747b28c;
        for (byte b : data) {
            int k = b & 0xFF;
            k *= 0xcc9e2d51;
            k = Integer.rotateLeft(k, 15);
            k *= 0x1b873593;
            h ^= k;
            h = Integer.rotateLeft(h, 13);
            h = h * 5 + 0xe6546b64;
        }
        h ^= data.length;
        h ^= (h >>> 16);
        h *= 0x85ebca6b;
        h ^= (h >>> 13);
        h *= 0xc2b2ae35;
        h ^= (h >>> 16);
        return h;
    }
}
