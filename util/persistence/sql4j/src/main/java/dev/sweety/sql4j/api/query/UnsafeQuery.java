package dev.sweety.sql4j.api.query;

public sealed interface UnsafeQuery<T> extends Query<T> permits AbstractUnsafeQuery {}
