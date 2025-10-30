package dev.sweety.core.event.interfaces;

@FunctionalInterface
public interface Operation<R> {
    R call(Object... var1);
}
