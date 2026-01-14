package dev.sweety.sql4j.api.query.chain;

import dev.sweety.sql4j.api.query.Query;

public interface ChainableQuery<T> {
    Query<T> query();
}
