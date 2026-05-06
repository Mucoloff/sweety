package dev.sweety.sql4j.entity;

import dev.sweety.sql4j.api.obj.TableAccessor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class SQL4JBenchmarkTest {

    private static final int ITERATIONS = 10_000_000;
    private static final int WARMUP = 1_000_000;

    @Test
    public void runBenchmarks() throws Exception {
        User user = new User();
        
        // 1. Generated Mirror Accessor
        TableAccessor<User> generatedAccessor = UserTable.INSTANCE;

        // 2. Reflective Accessor (Simulated)
        final Map<String, Field> fields = new HashMap<>();
        for (Field f : User.class.getDeclaredFields()) {
            f.setAccessible(true);
            fields.put(f.getName(), f);
        }

        System.out.println("--- SQL4J Field Access Benchmark (" + ITERATIONS + " iterations) ---");

        // Warmup
        for (int i = 0; i < WARMUP; i++) {
            generatedAccessor.setFieldValue(user, "name", "Charlie");
            fields.get("name").set(user, "Charlie");
        }

        // Benchmark Generated
        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            generatedAccessor.setFieldValue(user, "name", "Charlie");
        }
        long end = System.nanoTime();
        double generatedTime = (end - start) / 1_000_000.0;
        System.out.printf("Generated Mirror (Switch): %.2fms%n", generatedTime);

        // Benchmark Reflective
        start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            fields.get("name").set(user, "Charlie");
        }
        end = System.nanoTime();
        double reflectiveTime = (end - start) / 1_000_000.0;
        System.out.printf("Reflective (Field.set): %.2fms%n", reflectiveTime);

        double speedup = reflectiveTime / generatedTime;
        System.out.printf("Field Access Speedup: %.2fx%n", speedup);
        System.out.println("---------------------------------------------------------");
    }
}
