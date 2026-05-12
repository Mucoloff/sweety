package dev.sweety.sql4j.api;

import dev.sweety.sql4j.api.annotation.Cacheable;
import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.impl.cache.EntityCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies thread-safety of {@link EntityCache} (backed by Caffeine) under concurrent
 * read/write/evict access using virtual-thread {@link CompletableFuture}s.
 *
 * <p>No database connection required.
 */
class EntityCacheConcurrencyTest {

    // Minimal entity annotated with @Cacheable so EntityCache will service it
    @Cacheable(maxSize = 500)
    @Table.Info(name = "dummy_concurrent")
    static class DummyEntity {
        @Column.Info(name = "id", primaryKey = true)
        int id;
        @Column.Info(name = "name")
        String name;
        DummyEntity() {}
        DummyEntity(int id, String name) { this.id = id; this.name = name; }
    }

    private EntityCache cache;

    @BeforeEach
    void fresh() {
        cache = new EntityCache();
    }

    // ─── Basic sanity ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("put + get returns the same instance")
    void putAndGet_returnsSameInstance() {
        DummyEntity e = new DummyEntity(1, "alpha");
        cache.put(DummyEntity.class, 1, e);
        assertSame(e, cache.get(DummyEntity.class, 1));
    }

    @Test
    @DisplayName("get on absent key returns null")
    void get_absent_returnsNull() {
        assertNull(cache.get(DummyEntity.class, 999));
    }

    @Test
    @DisplayName("evict removes a specific entry; others survive")
    void evict_specificallyRemovesOneEntry() {
        cache.put(DummyEntity.class, 1, new DummyEntity(1, "a"));
        cache.put(DummyEntity.class, 2, new DummyEntity(2, "b"));

        cache.evict(DummyEntity.class, 1);

        assertNull(cache.get(DummyEntity.class, 1), "evicted entry must be gone");
        assertNotNull(cache.get(DummyEntity.class, 2), "non-evicted entry must survive");
    }

    @Test
    @DisplayName("evictAll removes every entry for the class")
    void evictAll_removesAllEntries() {
        for (int i = 0; i < 10; i++) cache.put(DummyEntity.class, i, new DummyEntity(i, "x"));
        cache.evictAll(DummyEntity.class);
        for (int i = 0; i < 10; i++) assertNull(cache.get(DummyEntity.class, i));
    }

    @Test
    @DisplayName("clear() removes all entries across all entity classes")
    void clear_removesAllClasses() {
        cache.put(DummyEntity.class, 1, new DummyEntity(1, "a"));
        cache.clear();
        assertNull(cache.get(DummyEntity.class, 1));
    }

    // ─── Concurrent access ───────────────────────────────────────────────────────

    @Test
    @DisplayName("concurrent put/get — no data races, all reads see a non-null value")
    void concurrent_putGet_noDataRace() throws Exception {
        final int THREADS = 50;
        final int ENTRIES_PER_THREAD = 20;

        // Pre-populate so reads always find something
        for (int i = 0; i < THREADS * ENTRIES_PER_THREAD; i++) {
            cache.put(DummyEntity.class, i, new DummyEntity(i, "pre-" + i));
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        AtomicInteger nullReads = new AtomicInteger();

        for (int t = 0; t < THREADS; t++) {
            final int base = t * ENTRIES_PER_THREAD;
            // Writer task
            futures.add(CompletableFuture.runAsync(() -> {
                for (int i = base; i < base + ENTRIES_PER_THREAD; i++) {
                    cache.put(DummyEntity.class, i, new DummyEntity(i, "updated-" + i));
                }
            }, Thread.ofVirtual().factory()::newThread));
            // Reader task (reads the SAME keys the writer is updating)
            futures.add(CompletableFuture.runAsync(() -> {
                for (int i = base; i < base + ENTRIES_PER_THREAD; i++) {
                    if (cache.get(DummyEntity.class, i) == null) {
                        nullReads.incrementAndGet();
                    }
                }
            }, Thread.ofVirtual().factory()::newThread));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Caffeine is Caffeine-safe: a concurrent put must not make an existing key return null
        // (it may return either the old or the new value, but NOT null)
        assertEquals(0, nullReads.get(),
                "Concurrent put/get must never return null for a pre-populated key");
    }

    @Test
    @DisplayName("concurrent evict/put — cache never goes into a corrupt state")
    void concurrent_evictPut_noCorruption() throws Exception {
        final int THREADS = 30;

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            final int key = t % 10; // Use only 10 keys → high contention
            futures.add(CompletableFuture.runAsync(() -> {
                cache.put(DummyEntity.class, key, new DummyEntity(key, "v"));
                cache.evict(DummyEntity.class, key);
                cache.put(DummyEntity.class, key, new DummyEntity(key, "v2"));
            }, Thread.ofVirtual().factory()::newThread));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // No assertion on the final value (concurrent races may put or evict last),
        // but the cache must not throw or corrupt internal state.
        assertDoesNotThrow(() -> cache.get(DummyEntity.class, 0));
    }

    @Test
    @DisplayName("concurrent evictAll — subsequent puts are visible to gets")
    void concurrent_evictAll_clearsAndRebuildsSafely() throws Exception {
        final int N = 20;
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // Writer: repeatedly puts all keys
        futures.add(CompletableFuture.runAsync(() -> {
            for (int round = 0; round < 5; round++) {
                for (int i = 0; i < N; i++) {
                    cache.put(DummyEntity.class, i, new DummyEntity(i, "r" + round));
                }
            }
        }, Thread.ofVirtual().factory()::newThread));

        // Clearer: repeatedly evicts all
        futures.add(CompletableFuture.runAsync(() -> {
            for (int round = 0; round < 5; round++) {
                cache.evictAll(DummyEntity.class);
            }
        }, Thread.ofVirtual().factory()::newThread));

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // After all concurrent activity finishes, the cache must be self-consistent.
        // Put a known value and immediately read it back on the same thread.
        DummyEntity known = new DummyEntity(42, "known");
        cache.put(DummyEntity.class, 42, known);
        assertSame(known, cache.get(DummyEntity.class, 42),
                "Cache must be consistent after concurrent evictAll + put storm");
    }

    // ─── isCacheable guard ───────────────────────────────────────────────────────

    @Test
    @DisplayName("non-@Cacheable entity — put/get are no-ops")
    void nonCacheableEntity_putsAndGetsAreNoops() {
        // No @Cacheable annotation on String
        cache.put(String.class, "key", "value"); // should be silently ignored
        assertNull(cache.get(String.class, "key"), "Non-@Cacheable put must have no effect");
    }

    @Test
    @DisplayName("setEnabled(false) — all puts are silently dropped")
    void disabled_putsAreDropped() {
        cache.setEnabled(false);
        cache.put(DummyEntity.class, 1, new DummyEntity(1, "x"));
        assertNull(cache.get(DummyEntity.class, 1), "Cache must be a no-op when disabled");
    }
}
