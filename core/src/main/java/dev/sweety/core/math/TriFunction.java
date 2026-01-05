package dev.sweety.core.math;

public interface TriFunction<R, T, U, V> extends Operation<R> {

    R apply(T t, U u, V v);

    @Override
    default R call(Object... args) {
        if (args.length != 3)
            throw new IllegalArgumentException("Invalid number of arguments: (expected: 3, found: " + args.length + ")");
        //noinspection unchecked
        return apply((T) args[0], (U) args[1], (V) args[2]);
    }
}
