package dev.sweety.serialization;

public interface Serializer<T, S, D> extends Writer<T, S>, Reader<T, D> {}
