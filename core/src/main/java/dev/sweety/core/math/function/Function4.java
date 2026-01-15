package dev.sweety.core.math.function;

public interface Function4<R, T, U, V, W> extends Operation<R> {

    R apply(T t, U u, V v, W w);

    @Override
    default R call(Object... args) {
        if (args.length != 4)
            throw new IllegalArgumentException("Invalid number of arguments: (expected: 4, found: " + args.length + ")");
        //noinspection unchecked
        return apply((T) args[0], (U) args[1], (V) args[2], (W) args[3]);
    }
}
