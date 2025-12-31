package dev.sweety.core.event.interfaces;

import dev.sweety.core.event.info.State;
import dev.sweety.core.event.info.Priority;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface LinkEvent {

    Priority level() default Priority.NORMAL;

    int priority() default -1;

    State state() default State.PRE;
}