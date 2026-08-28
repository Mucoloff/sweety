package dev.sweety.sql4j.api.obj;

public enum PrimitiveKind {
    BOOLEAN,
    BYTE,
    SHORT,
    INT,
    LONG,
    FLOAT,
    DOUBLE,
    CHAR,
    OBJECT;

    public static PrimitiveKind from(Class<?> type) {
        if (type == boolean.class) return BOOLEAN;
        if (type == byte.class) return BYTE;
        if (type == short.class) return SHORT;
        if (type == int.class) return INT;
        if (type == long.class) return LONG;
        if (type == float.class) return FLOAT;
        if (type == double.class) return DOUBLE;
        if (type == char.class) return CHAR;
        return OBJECT;
    }
}
