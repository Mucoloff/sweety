package dev.sweety.sql4j.api;

import dev.sweety.sql4j.api.repository.ReadOnlyRepository;
import dev.sweety.sql4j.api.repository.Repository;
import dev.sweety.sql4j.impl.BaseRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Compile-only and structural assertions for the {@link ReadOnlyRepository} extraction.
 * No database connection required.
 */
class ReadOnlyRepositoryTest {

    /**
     * {@link Repository} must extend {@link ReadOnlyRepository}.
     */
    @Test
    void repository_extendsReadOnlyRepository() {
        assertTrue(ReadOnlyRepository.class.isAssignableFrom(Repository.class),
                "Repository must extend ReadOnlyRepository");
    }

    /**
     * {@link BaseRepository} must satisfy {@link ReadOnlyRepository} at runtime.
     */
    @Test
    void baseRepository_implementsReadOnlyRepository() {
        assertTrue(ReadOnlyRepository.class.isAssignableFrom(BaseRepository.class),
                "BaseRepository must implement ReadOnlyRepository (transitively via Repository)");
    }

    /**
     * {@link ReadOnlyRepository} must not declare any mutating method names
     * (insert, update, delete, upsert, createTable, dropTable).
     */
    @Test
    void readOnlyRepository_hasNoMutatingMethods() {
        Method[] methods = ReadOnlyRepository.class.getDeclaredMethods();
        for (Method m : methods) {
            String name = m.getName();
            assertFalse(name.startsWith("insert"), "ReadOnlyRepository must not expose " + name);
            assertFalse(name.startsWith("update"), "ReadOnlyRepository must not expose " + name);
            assertFalse(name.startsWith("delete"), "ReadOnlyRepository must not expose " + name);
            assertFalse(name.startsWith("upsert"), "ReadOnlyRepository must not expose " + name);
            assertFalse(name.startsWith("createTable"), "ReadOnlyRepository must not expose " + name);
            assertFalse(name.startsWith("dropTable"), "ReadOnlyRepository must not expose " + name);
        }
    }
}
