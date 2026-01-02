package dev.sweety.core.math;

import java.util.function.Function;

@FunctionalInterface
public interface Operation<R> extends Function<Object[], R> {
    R call(Object... args);

    @Override
    default R apply(Object... args){
        return call(args);
    }
}
