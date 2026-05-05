package dev.sweety.sql4j.api.obj;

import java.util.List;

/**
 * Rappresenta le colonne di una tabella che sono esplicitamente inseribili
 * (esclude quindi quelle auto-increment per cui il database provvede da solo).
 */
public record InsertableColumns(List<Column> columns, Column autoIncrementColumn) {
    public boolean hasAutoIncrement() {
        return autoIncrementColumn != null;
    }
}
