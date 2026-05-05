package dev.sweety.sql4j.api.obj.annotation;

import dev.sweety.sql4j.api.obj.ForeignKey;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ManyToOne {
    String columnName() default "";
    ForeignKey.Action onDelete() default ForeignKey.Action.NO_ACTION;
    ForeignKey.Action onUpdate() default ForeignKey.Action.NO_ACTION;
}
