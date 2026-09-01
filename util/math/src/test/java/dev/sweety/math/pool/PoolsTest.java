package dev.sweety.math.pool;

import dev.sweety.math.pool.leak.ResourceLeakDetector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class PoolsTest {

    @BeforeEach
    public void setUp() {
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);
    }

    @AfterEach
    public void tearDown() {
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED);
        ResourceLeakDetector.setLeakListener(null);
    }

    @Test
    public void testForeignClassStringBuilderPooling() {
        try (PoolHandle<StringBuilder> handle = Pools.borrow(StringBuilder.class)) {
            StringBuilder sb = handle.get();
            sb.append("hello-sweety");
            assertEquals("hello-sweety", sb.toString());
        }

        // Borrow again — verify it was reset and is clean
        try (PoolHandle<StringBuilder> handle = Pools.borrow(StringBuilder.class)) {
            StringBuilder sb = handle.get();
            assertEquals(0, sb.length());
        }
    }

    @Test
    public void testCustomForeignClassRegistration() {
        Pools.register(ArrayList.class, ArrayList::new, ArrayList::clear);

        try (PoolHandle<ArrayList> handle = Pools.borrow(ArrayList.class)) {
            ArrayList list = handle.get();
            list.add("item1");
            assertEquals(1, list.size());
        }

        try (PoolHandle<ArrayList> handle = Pools.borrow(ArrayList.class)) {
            ArrayList list = handle.get();
            assertEquals(0, list.size());
        }
    }

    @Test
    public void testResourceLeakDetectorCatchesUnreleasedObject() throws Exception {
        ResourceLeakDetector<StringBuilder> detector = new ResourceLeakDetector<>(StringBuilder.class);
        AtomicReference<String> detectedLeak = new AtomicReference<>();
        ResourceLeakDetector.setLeakListener(detectedLeak::set);

        // Allocate in separate scope and deliberately drop without release
        allocateAndDrop(detector);

        // Trigger GC to collect the leaked object
        for (int i = 0; i < 5; i++) {
            System.gc();
            detector.reportLeaks();
            if (detectedLeak.get() != null) break;
            Thread.sleep(50);
        }

        assertNotNull(detectedLeak.get(), "Leak detector should have captured the leaked StringBuilder");
        assertTrue(detectedLeak.get().contains("LEAK: Object of type StringBuilder was GC-collected"));
        assertTrue(detectedLeak.get().contains("allocateAndDrop"));
    }

    private void allocateAndDrop(ResourceLeakDetector<StringBuilder> detector) {
        StringBuilder leaked = new StringBuilder("leaked-data");
        detector.track(leaked);
        // Do not release 'leaked'
    }
}
