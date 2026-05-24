package dev.sweety.netty.packet.model;

import io.netty.util.Recycler;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class PacketPool {

    private static final ConcurrentHashMap<Class<?>, Recycler<?>> REGISTRY = new ConcurrentHashMap<>();

    private PacketPool() {}

    @SuppressWarnings("unchecked")
    public static <T extends Pooled<T>> T acquire(
            final Class<T> cls,
            final Function<Recycler.Handle<T>, T> ctor) {
        final Recycler<T> pool = (Recycler<T>) REGISTRY.computeIfAbsent(cls, c -> new Recycler<T>() {
            @Override
            protected T newObject(final Handle<T> h) {
                return ctor.apply(h);
            }
        });
        return pool.get();
    }
}
