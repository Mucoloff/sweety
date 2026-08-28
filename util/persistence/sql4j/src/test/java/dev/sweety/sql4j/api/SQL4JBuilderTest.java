package dev.sweety.sql4j.api;

import dev.sweety.sql4j.SQL4J;
import dev.sweety.sql4j.api.configuration.SQL4JConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the {@link SQL4J} step builder.
 * No database connection required.
 */
class SQL4JBuilderTest {

    /**
     * Compile-time guarantee: {@link SQL4J.DriverStep} must NOT declare {@code open()}.
     * If this test fails, the step contract has been broken.
     */
    @Test
    void driverStep_doesNotDeclare_open() {
        boolean hasOpen = Arrays.stream(SQL4J.DriverStep.class.getDeclaredMethods())
                .anyMatch(m -> m.getName().equals("open"));
        assertFalse(hasOpen, "DriverStep must not expose open() — callers must pick a driver first");
    }

    /**
     * {@link SQL4J.OpenStep} must declare {@code open()} exactly once.
     */
    @Test
    void openStep_declares_open() {
        long count = Arrays.stream(SQL4J.OpenStep.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("open"))
                .count();
        assertEquals(1, count, "OpenStep must declare open() exactly once");
    }

    /**
     * {@link SQL4J.OpenStep} must declare {@code pool(Consumer)} to expose HikariCP tuning.
     */
    @Test
    void openStep_declares_pool() {
        boolean hasPool = Arrays.stream(SQL4J.OpenStep.class.getDeclaredMethods())
                .anyMatch(m -> m.getName().equals("pool"));
        assertTrue(hasPool, "OpenStep must expose pool(Consumer<HikariTuningBuilder>)");
    }

    /**
     * {@link SQL4J.OpenStep} must declare {@code withHikariConfig}.
     */
    @Test
    void openStep_declares_withHikariConfig() {
        boolean hasRaw = Arrays.stream(SQL4J.OpenStep.class.getDeclaredMethods())
                .anyMatch(m -> m.getName().equals("withHikariConfig"));
        assertTrue(hasRaw, "OpenStep must expose withHikariConfig(HikariConfig)");
    }

    /**
     * {@link SQL4J.OpenStep#build()} must return {@link SQL4JConfig}.
     */
    @Test
    void openStep_build_returnsSql4jConfig() throws NoSuchMethodException {
        Method buildMethod = SQL4J.OpenStep.class.getDeclaredMethod("build");
        assertEquals(SQL4JConfig.class, buildMethod.getReturnType(),
                "OpenStep.build() must return SQL4JConfig");
    }

    /**
     * {@link SQL4J#connect()} must return {@link SQL4J.DriverStep}, not {@link SQL4J.OpenStep}.
     */
    @Test
    void connect_returnsDriverStep() throws NoSuchMethodException {
        Method connectMethod = SQL4J.class.getDeclaredMethod("connect");
        assertEquals(SQL4J.DriverStep.class, connectMethod.getReturnType(),
                "SQL4J.connect() must return DriverStep");
    }
}
