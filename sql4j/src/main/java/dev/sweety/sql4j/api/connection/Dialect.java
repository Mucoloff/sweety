package dev.sweety.sql4j.api.connection;

public interface Dialect {

    String name();

    String sqlType(Class<?> javaType);

    String autoIncrement();

    default boolean supportsIfNotExists() {
        return true;
    }

    default boolean supportsGeneratedKeys() {
        return true;
    }

    default boolean inlinePrimaryKeyForAutoIncrement() {
        return false;
    }
}

