package dev.sweety.sql4j.api.obj;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public record ForeignKey(
        Column local,
        Table<?> referencedTable,
        Column referencedColumn,
        boolean nullable,
        Action onDelete,
        Action onUpdate
) {
    public enum Action {
        CASCADE, SET_NULL, RESTRICT, NO_ACTION
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Info {
        Class<?> table();
        String column() default "id";
        ForeignKey.Action onDelete() default ForeignKey.Action.NO_ACTION;
        ForeignKey.Action onUpdate() default ForeignKey.Action.NO_ACTION;
    }

}
