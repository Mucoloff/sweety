package dev.sweety.netty.routing;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic O(1) Virtual Shard Router adapter aligned with SQL4J's {@code VirtualShardRouter}.
 * Partitions key space into virtual slots (default 8192, a power of 2) using bitwise masking:
 * {@code virtualShard = hash(key) & (totalShards - 1)}.
 *
 * <p>Enables stateful, sharded routing for database RPC (SQL4J) and sticky-session clusters
 * without runtime GC allocation.
 *
 * @param <T> incoming message or context type
 * @param <C> target channel or backend node type
 */
public class VirtualShardRouterAdapter<T, C> implements Router<T, C> {

    public static final int DEFAULT_SHARDS = 8192; // 2^13

    private final int totalShards;
    private final int mask;
    private final ShardKeyExtractor<T> keyExtractor;

    public VirtualShardRouterAdapter(@NotNull ShardKeyExtractor<T> keyExtractor) {
        this(DEFAULT_SHARDS, keyExtractor);
    }

    public VirtualShardRouterAdapter(int totalShards, @NotNull ShardKeyExtractor<T> keyExtractor) {
        if (totalShards <= 0 || (totalShards & (totalShards - 1)) != 0) {
            throw new IllegalArgumentException("totalShards must be a positive power of 2: " + totalShards);
        }
        this.totalShards = totalShards;
        this.mask = totalShards - 1;
        this.keyExtractor = Objects.requireNonNull(keyExtractor, "keyExtractor cannot be null");
    }

    @Override
    public C route(@NotNull T message, @NotNull List<C> targets) {
        if (targets.isEmpty()) {
            return null;
        }
        if (targets.size() == 1) {
            return targets.get(0);
        }
        long key = keyExtractor.extractKey(message);
        int slot = hashLong(key) & mask;
        int targetIndex = slot % targets.size();
        return targets.get(targetIndex);
    }

    public int resolveSlot(long key) {
        return hashLong(key) & mask;
    }

    public int resolveSlot(@NotNull String key) {
        return murmur3(key.getBytes(StandardCharsets.UTF_8)) & mask;
    }

    public int totalShards() {
        return totalShards;
    }

    private static int hashLong(long key) {
        key = (~key) + (key << 21);
        key = key ^ (key >>> 24);
        key = (key + (key << 3)) + (key << 8);
        key = key ^ (key >>> 14);
        key = (key + (key << 2)) + (key << 4);
        key = key ^ (key >>> 28);
        key = key + (key << 31);
        return (int) (key ^ (key >>> 32));
    }

    private static int murmur3(byte[] data) {
        int h = 0x9747b28c;
        int len = data.length;
        int nblocks = len >> 2;

        for (int i = 0; i < nblocks; i++) {
            int i4 = i << 2;
            int k = (data[i4] & 0xff)
                    | ((data[i4 + 1] & 0xff) << 8)
                    | ((data[i4 + 2] & 0xff) << 16)
                    | ((data[i4 + 3] & 0xff) << 24);

            k *= 0xcc9e2d51;
            k = Integer.rotateLeft(k, 15);
            k *= 0x1b873593;

            h ^= k;
            h = Integer.rotateLeft(h, 13);
            h = h * 5 + 0xe6546b64;
        }

        int k = 0;
        int tail = nblocks << 2;
        switch (len & 3) {
            case 3 -> {
                k ^= (data[tail + 2] & 0xff) << 16;
                k ^= (data[tail + 1] & 0xff) << 8;
                k ^= (data[tail] & 0xff);
                k *= 0xcc9e2d51;
                k = Integer.rotateLeft(k, 15);
                k *= 0x1b873593;
                h ^= k;
            }
            case 2 -> {
                k ^= (data[tail + 1] & 0xff) << 8;
                k ^= (data[tail] & 0xff);
                k *= 0xcc9e2d51;
                k = Integer.rotateLeft(k, 15);
                k *= 0x1b873593;
                h ^= k;
            }
            case 1 -> {
                k ^= (data[tail] & 0xff);
                k *= 0xcc9e2d51;
                k = Integer.rotateLeft(k, 15);
                k *= 0x1b873593;
                h ^= k;
            }
        }

        h ^= len;
        h ^= h >>> 16;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;
        return h;
    }
}
