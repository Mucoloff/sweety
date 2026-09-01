package dev.sweety.config.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an interface for automatic Type-Safe Configuration class generation.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface GenerateConfig {
    String file() default "";
    ConfigFormat format() default ConfigFormat.YAML;
}
