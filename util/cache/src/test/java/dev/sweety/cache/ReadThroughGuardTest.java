package dev.sweety.cache;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadThroughGuardTest {

    private static GuardConfig config() {
        return GuardConfig.of(Duration.ofMinutes(5), Duration.ofMinutes(5));
    }

    @Test
    void unknownKeyShortCircuitsBeforeLoader() {
        AtomicInteger loads = new AtomicInteger();
        ReadThroughGuard<String, String> guard = ReadThroughGuard.create(
                config(),
                k -> k.getBytes(StandardCharsets.UTF_8),
                k -> { loads.incrementAndGet(); return Optional.empty(); });
        guard.seed(java.util.List.of()); // arm with empty store

        // bloom is empty → key cannot exist → loader must NOT run (cache-penetration defense)
        assertTrue(guard.get("ghost").isEmpty());
        assertEquals(0, loads.get(), "loader must not be hit for a key absent from the bloom");
    }

    @Test
    void seededKeyLoadsOnceThenCaches() {
        Map<String, String> backing = Map.of("a", "1");
        AtomicInteger loads = new AtomicInteger();
        ReadThroughGuard<String, String> guard = ReadThroughGuard.create(
                config(),
                k -> k.getBytes(StandardCharsets.UTF_8),
                k -> { loads.incrementAndGet(); return Optional.ofNullable(backing.get(k)); });

        guard.seed(backing.keySet());

        assertEquals(Optional.of("1"), guard.get("a"));
        assertEquals(Optional.of("1"), guard.get("a"));
        assertEquals(1, loads.get(), "second hit must be served from cache");
    }

    @Test
    void negativeCachingAvoidsRepeatLoaderForBloomFalsePositive() {
        AtomicInteger loads = new AtomicInteger();
        ReadThroughGuard<String, String> guard = ReadThroughGuard.create(
                config(),
                k -> k.getBytes(StandardCharsets.UTF_8),
                k -> { loads.incrementAndGet(); return Optional.empty(); });

        guard.seed(java.util.List.of()); // arm the bloom
        // force the key past the bloom even though the loader will say "absent"
        guard.remember("x");

        assertTrue(guard.get("x").isEmpty());
        assertTrue(guard.get("x").isEmpty());
        assertEquals(1, loads.get(), "absent result must be negatively cached");
    }

    @Test
    void unseededGuardIsFailOpenNotFalseAbsent() {
        Map<String, String> backing = Map.of("a", "1");
        AtomicInteger loads = new AtomicInteger();
        // NOTE: no seed() called → bloom must NOT be trusted, existing keys still resolve via loader.
        ReadThroughGuard<String, String> guard = ReadThroughGuard.create(
                config(),
                k -> k.getBytes(StandardCharsets.UTF_8),
                k -> { loads.incrementAndGet(); return Optional.ofNullable(backing.get(k)); });

        assertEquals(Optional.of("1"), guard.get("a"), "forgotten seed must not cause false-absent");
        assertTrue(loads.get() >= 1);
    }

    @Test
    void seededFactoryArmsAndShortCircuits() {
        AtomicInteger loads = new AtomicInteger();
        ReadThroughGuard<String, String> guard = ReadThroughGuard.seeded(
                config(),
                k -> k.getBytes(StandardCharsets.UTF_8),
                k -> { loads.incrementAndGet(); return Optional.empty(); },
                () -> java.util.List.of("a"));

        assertTrue(guard.get("ghost").isEmpty());
        assertEquals(0, loads.get(), "armed bloom must short-circuit unknown keys");
    }

    @Test
    void invalidateForcesReload() {
        AtomicInteger loads = new AtomicInteger();
        String[] value = {"old"};
        ReadThroughGuard<String, String> guard = ReadThroughGuard.create(
                config(),
                k -> k.getBytes(StandardCharsets.UTF_8),
                k -> { loads.incrementAndGet(); return Optional.of(value[0]); });
        guard.remember("k");

        assertEquals(Optional.of("old"), guard.get("k"));
        value[0] = "new";
        guard.invalidate("k");
        assertEquals(Optional.of("new"), guard.get("k"));
        assertEquals(2, loads.get());
    }

    @Test
    void forgetRemovesFromBloomAndCache() {
        ReadThroughGuard<String, String> guard = ReadThroughGuard.create(
                config(),
                k -> k.getBytes(StandardCharsets.UTF_8),
                k -> Optional.of("v"));
        guard.seed(java.util.List.of("k")); // arm + seed existing key
        assertEquals(Optional.of("v"), guard.get("k"));

        guard.forget("k");
        assertTrue(guard.get("k").isEmpty(), "forgotten key should short-circuit as absent");
    }
}
