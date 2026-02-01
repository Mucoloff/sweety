package dev.sweety.record.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.SOURCE)
public @interface Setter {

    boolean applyAll() default true;

    boolean includeStatic() default false;

    Type[] types() default {Type.DEFAULT};

    enum Type {
        DEFAULT(true, false), FLUENT(false, false), BUILDER(true, true), BUILDER_FLUENT(false, true);

        Type(boolean classic, boolean builder) {
            this.classic = classic;
            this.builder = builder;
        }

        final boolean classic, builder;

        public boolean classic() {
            return classic;
        }

        public boolean builder() {
            return builder;
        }

    }

}

