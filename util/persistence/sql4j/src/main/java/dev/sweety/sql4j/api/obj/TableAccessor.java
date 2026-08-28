package dev.sweety.sql4j.api.obj;

/**
 * Interface per l'accesso ai campi delle entità senza reflection né boxing.
 * Implementata dalle classi generate (es. UserTable).
 */
public interface TableAccessor<T> {
    T newInstance();

    // ── Primitive Setters (Zero-Boxing da JDBC ResultSet) ──
    default void setBoolean(T instance, int colIndex, boolean value) {
        throw new UnsupportedOperationException("setBoolean not supported for colIndex " + colIndex);
    }

    default void setByte(T instance, int colIndex, byte value) {
        throw new UnsupportedOperationException("setByte not supported for colIndex " + colIndex);
    }

    default void setShort(T instance, int colIndex, short value) {
        throw new UnsupportedOperationException("setShort not supported for colIndex " + colIndex);
    }

    default void setInt(T instance, int colIndex, int value) {
        throw new UnsupportedOperationException("setInt not supported for colIndex " + colIndex);
    }

    default void setLong(T instance, int colIndex, long value) {
        throw new UnsupportedOperationException("setLong not supported for colIndex " + colIndex);
    }

    default void setFloat(T instance, int colIndex, float value) {
        throw new UnsupportedOperationException("setFloat not supported for colIndex " + colIndex);
    }

    default void setDouble(T instance, int colIndex, double value) {
        throw new UnsupportedOperationException("setDouble not supported for colIndex " + colIndex);
    }

    default void setChar(T instance, int colIndex, char value) {
        throw new UnsupportedOperationException("setChar not supported for colIndex " + colIndex);
    }

    default void setObject(T instance, int colIndex, Object value) {
        throw new UnsupportedOperationException("setObject not supported for colIndex " + colIndex);
    }

    // ── Primitive Getters (Zero-Boxing verso PreparedStatement / Bind) ──
    default boolean getBoolean(T instance, int colIndex) {
        throw new UnsupportedOperationException("getBoolean not supported for colIndex " + colIndex);
    }

    default byte getByte(T instance, int colIndex) {
        throw new UnsupportedOperationException("getByte not supported for colIndex " + colIndex);
    }

    default short getShort(T instance, int colIndex) {
        throw new UnsupportedOperationException("getShort not supported for colIndex " + colIndex);
    }

    default int getInt(T instance, int colIndex) {
        throw new UnsupportedOperationException("getInt not supported for colIndex " + colIndex);
    }

    default long getLong(T instance, int colIndex) {
        throw new UnsupportedOperationException("getLong not supported for colIndex " + colIndex);
    }

    default float getFloat(T instance, int colIndex) {
        throw new UnsupportedOperationException("getFloat not supported for colIndex " + colIndex);
    }

    default double getDouble(T instance, int colIndex) {
        throw new UnsupportedOperationException("getDouble not supported for colIndex " + colIndex);
    }

    default char getChar(T instance, int colIndex) {
        throw new UnsupportedOperationException("getChar not supported for colIndex " + colIndex);
    }

    default Object getObject(T instance, int colIndex) {
        throw new UnsupportedOperationException("getObject not supported for colIndex " + colIndex);
    }

    // ── Legacy / String-based fallback bridges ──
    default void setFieldValue(T instance, String colName, Object value) {
        throw new UnsupportedOperationException("setFieldValue by name not supported: " + colName);
    }

    default Object getFieldValue(T instance, String colName) {
        throw new UnsupportedOperationException("getFieldValue by name not supported: " + colName);
    }

    default String getInsertSql() {
        return null;
    }

    default String getUpdateSql() {
        return null;
    }

    default String getDeleteSql() {
        return null;
    }

    default String getSelectAllSql() {
        return null;
    }
}
