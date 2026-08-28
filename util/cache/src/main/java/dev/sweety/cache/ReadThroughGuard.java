package dev.sweety.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import dev.sweety.filter.FastScalableCountingBloomFilter;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Three-layer read-through guard for an expensive backing store (typically a database):
 * <pre>
 *   bloom filter ──▶ cache (with negative caching) ──▶ loader (DB)
 * </pre>
 *
 * <ol>
 *   <li><b>Bloom filter</b> — a {@link FastScalableCountingBloomFilter} that holds every key known to
 *       exist. A key absent from the bloom is <em>definitely</em> absent from the store, so the lookup
 *       returns empty without touching the cache or the loader. This is the cache-penetration defense:
 *       floods of lookups for non-existent keys never reach the database.</li>
 *   <li><b>Cache</b> — a Caffeine cache of {@link Optional} values. A present {@code Optional} caches a
 *       hit; an empty {@code Optional} is the <b>negative cache</b> (the key passed the bloom — a false
 *       positive, or was just deleted — but the loader confirmed it is absent), so repeated misses for
 *       the same key are served from memory instead of re-querying.</li>
 *   <li><b>Loader</b> — invoked only on a true cache miss; its result populates the cache (and, when
 *       present, the bloom).</li>
 * </ol>
 *
 * <p><b>Correctness requirement:</b> a bloom filter never produces false negatives, but only if it has
 * been told about every existing key. {@link #seed(Collection)} the bloom with all current keys at
 * startup and call {@link #put(Object, Object)} / {@link #remember(Object)} on every insert, otherwise
 * a freshly-started guard reports existing keys as absent. Deletes should call
 * {@link #invalidate(Object)} (clears the cache) and, if the key is truly gone, {@link #forget(Object)}
 * (decrements the counting bloom).</p>
 *
 * <p>Thread-safe: the bloom filter and the Caffeine cache are both concurrent.</p>
 */
public final class ReadThroughGuard<K, V> {

    private final FastScalableCountingBloomFilter bloom;
    private final Cache<K, Optional<V>> cache;
    private final Function<K, byte[]> keyBytes;
    private final Function<K, Optional<V>> loader;

    /**
     * Bloom short-circuit is armed only once the bloom is known to hold every existing key (i.e. after
     * {@link #seed} / {@link #put} / {@link #remember}). Until then the guard is <b>fail-open</b>: it
     * skips the bloom and goes straight to cache→loader, so a forgotten seed degrades to a plain cache
     * (still correct) instead of reporting existing keys as absent.
     */
    private volatile boolean armed;

    private ReadThroughGuard(FastScalableCountingBloomFilter bloom,
                             Cache<K, Optional<V>> cache,
                             Function<K, byte[]> keyBytes,
                             Function<K, Optional<V>> loader) {
        this.bloom    = bloom;
        this.cache    = cache;
        this.keyBytes = keyBytes;
        this.loader   = loader;
    }

    /**
     * @param config   sizing/TTL parameters for the bloom and cache
     * @param keyBytes serializes a key to bytes for the bloom filter (must be stable per key)
     * @param loader   loads a key from the backing store; returns empty when the key is absent
     */
    public static <K, V> ReadThroughGuard<K, V> create(GuardConfig config,
                                                       Function<K, byte[]> keyBytes,
                                                       Function<K, Optional<V>> loader) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(keyBytes, "keyBytes");
        Objects.requireNonNull(loader, "loader");

        FastScalableCountingBloomFilter bloom = FastScalableCountingBloomFilter.of(
                config.bloomInitialBuckets(), config.bloomHashCount(), config.bloomGrowthFactor());

        Cache<K, Optional<V>> cache = Caffeine.newBuilder()
                .maximumSize(config.maxCacheSize())
                .expireAfter(new TtlExpiry<K, V>(config.valueTtlNanos(), config.absentTtlNanos()))
                .build();

        return new ReadThroughGuard<>(bloom, cache, keyBytes, loader);
    }

    /**
     * Eager-seeding factory: seeds the bloom from {@code seedSource} immediately and arms the
     * short-circuit, so a guard can never be left unseeded. Prefer this over {@link #create} +
     * manual {@link #seed}.
     *
     * @param seedSource supplies all keys known to exist at startup
     */
    public static <K, V> ReadThroughGuard<K, V> seeded(GuardConfig config,
                                                       Function<K, byte[]> keyBytes,
                                                       Function<K, Optional<V>> loader,
                                                       Supplier<? extends Collection<? extends K>> seedSource) {
        Objects.requireNonNull(seedSource, "seedSource");
        ReadThroughGuard<K, V> guard = create(config, keyBytes, loader);
        guard.seed(seedSource.get());
        return guard;
    }

    /** Looks the key up through all three layers, querying the loader only on a true miss. */
    public Optional<V> get(K key) {
        byte[] kb = keyBytes.apply(key);

        // Layer 1: bloom — a miss means the key cannot exist, short-circuit before cache/DB.
        // Only trusted once armed (seeded); an unarmed guard falls through to cache→loader.
        if (armed && !bloom.contains(kb)) {
            return Optional.empty();
        }

        // Layer 2: cache — a non-null entry is authoritative (empty Optional = negative cache hit).
        Optional<V> cached = cache.getIfPresent(key);
        if (cached != null) return cached;

        // Layer 3: loader (DB). Cache the outcome either way; keep the bloom in sync on a hit.
        Optional<V> loaded = loader.apply(key);
        cache.put(key, loaded);
        // Keep the bloom in sync on a hit, but guard against re-adding an already-counted key:
        // the counting bloom increments per add, so a blind add on every cache miss would inflate
        // counters and make a single forget() unable to clear the key.
        if (loaded.isPresent() && !bloom.contains(kb)) bloom.add(kb);
        return loaded;
    }

    /**
     * Seeds the bloom with all keys known to exist and arms the short-circuit. Call once at startup
     * (an empty collection is valid — it means the store is empty, so every key is correctly absent).
     */
    public void seed(Collection<? extends K> existingKeys) {
        for (K key : existingKeys) {
            addOnce(keyBytes.apply(key));
        }
        armed = true;
    }

    /** Records that a key now exists (bloom only). Use when inserting without a value to cache. */
    public void remember(K key) {
        addOnce(keyBytes.apply(key));
    }

    /** Records a key and primes its cached value. Call on insert/update. */
    public void put(K key, V value) {
        addOnce(keyBytes.apply(key));
        cache.put(key, Optional.of(value));
    }

    /** Adds to the counting bloom at most once per key, so {@link #forget} can later clear it. */
    private void addOnce(byte[] kb) {
        if (!bloom.contains(kb)) {
            bloom.add(kb);
        }
    }

    /** Drops the cached entry for a key (next {@link #get} re-loads). Use after an update. */
    public void invalidate(K key) {
        cache.invalidate(key);
    }

    /**
     * Marks a key as no longer existing: decrements the counting bloom and removes the cache entry.
     * Use on delete. (A standard bloom filter cannot remove; the counting variant can.)
     */
    public void forget(K key) {
        bloom.remove(keyBytes.apply(key));
        cache.invalidate(key);
    }

    /** Custom Caffeine {@link Expiry}: present values and negatively-cached absents get distinct TTLs. */
    private record TtlExpiry<K, V>(long valueTtlNanos, long absentTtlNanos)
            implements Expiry<K, Optional<V>> {

        @Override
        public long expireAfterCreate(K key, Optional<V> value, long currentTime) {
            return value.isPresent() ? valueTtlNanos : absentTtlNanos;
        }

        @Override
        public long expireAfterUpdate(K key, Optional<V> value, long currentTime, long currentDuration) {
            return value.isPresent() ? valueTtlNanos : absentTtlNanos;
        }

        @Override
        public long expireAfterRead(K key, Optional<V> value, long currentTime, long currentDuration) {
            return currentDuration;
        }
    }
}
