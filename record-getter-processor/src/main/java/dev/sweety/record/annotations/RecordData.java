package dev.sweety.record.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.SOURCE)
public @interface RecordData {

    boolean applyAll() default true;

    boolean includeStatic() default false;

    Setter.Type[] setterTypes() default {Setter.Type.DEFAULT};

    /**
     * Genera un costruttore con tutti i campi (ignorando quelli statici o marcati con @DataIgnore)
     */
    boolean allArgsConstructor() default false;

}
