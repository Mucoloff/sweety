package dev.sweety.sql4j.api.obj;

/**
 * Interface per l'accesso ai campi delle entità senza reflection.
 * Implementata dalle classi generate (es. UserTable).
 */
public interface TableAccessor<T> {
    T newInstance();
    void setFieldValue(T instance, String colName, Object value);
    Object getFieldValue(T instance, String colName);

    default String getInsertSql() { return null; }
    default String getUpdateSql() { return null; }
    default String getDeleteSql() { return null; }
    default String getSelectAllSql() { return null; }
}
