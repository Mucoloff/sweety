package dev.sweety.event.api.listener;

import dev.sweety.event.api.info.Priority;
import dev.sweety.event.api.info.State;

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

    boolean readOnly() default false;

    /**
     * Whether this listener may run on a worker thread (batched with other read-only listeners) or
     * MUST run synchronously on the dispatching thread. Default {@code true} keeps the multithreaded
     * fast path. Set {@code false} for listeners that touch Minecraft or shared non-thread-safe state
     * directly (e.g. per-tick systems) — they then run inline on the caller thread (the main thread
     * for tick events). For a listener that's mostly parallel-safe, defer just the unsafe mutation
     * with {@code mc.execute(...)} instead.
     */
    boolean parallel() default true;
}

