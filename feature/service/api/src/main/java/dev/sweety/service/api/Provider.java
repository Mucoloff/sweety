package dev.sweety.service.api;

import java.util.function.Supplier;

public interface Provider<T> extends Supplier<T> {

    @Override
    T get();
}