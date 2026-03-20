package dev.sweety.sql4j.api.query.chain;

import dev.sweety.sql4j.api.query.Query;

import java.util.function.Function;

@FunctionalInterface
public interface ChainableQuery<I, O> {
    Query<O> build(I previous);

    static <T> ChainableQuery<Object, T> ignore(Query<T> q) {
        return __ -> q;
    }

    static <I, O> ChainableQuery<I, O> step(
            Function<I, Query<O>> fn
    ) {
        return fn::apply;
    }

}

