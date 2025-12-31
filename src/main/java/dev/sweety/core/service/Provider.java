package dev.sweety.core.service;

import java.util.function.Supplier;

public interface Provider<T> extends Supplier<T> {

    @Override
    T get();
}