package dev.sweety.event.api.function;

@FunctionalInterface
public interface Operation<R> {
    R call(Object... args);
}
