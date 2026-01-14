package dev.sweety.sql4j.api.connection;

import dev.sweety.sql4j.api.obj.ForeignKey;

public interface Dialect {

    String name();

    String sqlType(Class<?> javaType);

    String autoIncrement();

    String foreignKeyAction(ForeignKey.Action action);

    default boolean supportsIfNotExists() {
        return true;
    }

    default boolean supportsGeneratedKeys() {
        return true;
    }

    default boolean inlinePrimaryKeyForAutoIncrement() {
        return false;
    }

    default boolean supportsForeignKeys() {
        return true;
    }
}

