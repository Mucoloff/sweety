package dev.sweety.sql4j.api;

import dev.sweety.sql4j.api.exception.*;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Sql4j exception hierarchy.
 * No database connection required.
 */
class Sql4jExceptionTest {

    @Test
    void rootException_preservesMessage() {
        Sql4jException ex = new Sql4jException("test message");
        assertEquals("test message", ex.getMessage());
    }

    @Test
    void rootException_preservesCause() {
        Throwable cause = new RuntimeException("root cause");
        Sql4jException ex = new Sql4jException("wrapper", cause);
        assertSame(cause, ex.getCause());
        assertEquals("wrapper", ex.getMessage());
    }

    @Test
    void rootException_fromCauseOnly() {
        Throwable cause = new RuntimeException("original");
        Sql4jException ex = new Sql4jException(cause);
        assertSame(cause, ex.getCause());
    }

    @Test
    void connectionException_isSubtype() {
        Sql4jConnectionException ex = new Sql4jConnectionException("no connection");
        assertInstanceOf(Sql4jException.class, ex);
        assertEquals("no connection", ex.getMessage());
    }

    @Test
    void mappingException_isSubtype() {
        Sql4jMappingException ex = new Sql4jMappingException("mapping failed");
        assertInstanceOf(Sql4jException.class, ex);
        assertEquals("mapping failed", ex.getMessage());
    }

    @Test
    void queryException_preservesSQLState() {
        SQLException sqlEx = new SQLException("constraint violation", "23000", 1062);
        Sql4jQueryException ex = new Sql4jQueryException("query failed", sqlEx);

        assertInstanceOf(Sql4jException.class, ex);
        assertEquals("query failed", ex.getMessage());
        assertSame(sqlEx, ex.getCause());
        assertEquals("23000", ex.getSqlState());
        assertEquals(1062, ex.getErrorCode());
    }

    @Test
    void queryException_withNullCause_hasZeroErrorCode() {
        Sql4jQueryException ex = new Sql4jQueryException("plain error");
        assertNull(ex.getSqlState());
        assertEquals(0, ex.getErrorCode());
    }

    @Test
    void cacheException_isSubtype() {
        Sql4jCacheException ex = new Sql4jCacheException("cache inconsistency");
        assertInstanceOf(Sql4jException.class, ex);
        assertEquals("cache inconsistency", ex.getMessage());
    }

    @Test
    void allSubtypesAreCatchableAsSql4jException() {
        assertDoesNotThrow(() -> {
            try {
                throw new Sql4jConnectionException("conn");
            } catch (Sql4jException ignored) {}
        });
        assertDoesNotThrow(() -> {
            try {
                throw new Sql4jMappingException("mapping");
            } catch (Sql4jException ignored) {}
        });
        assertDoesNotThrow(() -> {
            try {
                throw new Sql4jQueryException("query");
            } catch (Sql4jException ignored) {}
        });
        assertDoesNotThrow(() -> {
            try {
                throw new Sql4jCacheException("cache");
            } catch (Sql4jException ignored) {}
        });
    }
}
