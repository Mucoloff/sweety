package dev.sweety.data;


import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class Container<T> {

    private volatile T value;

    private Container() {
        this.value = null;
    }

    private Container(@NotNull T value) {
        this.value = Objects.requireNonNull(value);
    }

    public static <T> Container<T> empty() {
        return new Container<>();
    }

    public static <T> Container<T> of(@NotNull T value) {
        return new Container<>(value);
    }

    public static <T> Container<T> ofNullable(@Nullable T value) {
        Container<T> c = new Container<>();
        c.value = value;
        return c;
    }

    public void set(@NotNull T value) {
        this.value = Objects.requireNonNull(value);
    }

    public void setNullable(@Nullable T value) {
        this.value = value;
    }

    public void setOptional(@NotNull Optional<T> value) {
        this.value = value.orElse(null);
    }

    public Optional<T> optional() {
        return Optional.ofNullable(this.value);
    }

    public void clear() {
        this.value = null;
    }

    public @Nullable T getUnsafe() {
        return value;
    }

    public @NotNull T get() {
        if (value == null) throw new NoSuchElementException("No value present");
        return value;
    }

    public boolean isPresent() {
        return value != null;
    }

    public boolean isEmpty() {
        return value == null;
    }

    public void ifPresent(Consumer<? super T> action) {
        if (value != null) action.accept(value);
    }

    public void ifPresentOrElse(Consumer<? super T> action, Runnable emptyAction) {
        if (value != null) {
            action.accept(value);
        } else {
            emptyAction.run();
        }
    }

    public Container<T> filter(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate);
        if (isEmpty()) return this;
        return predicate.test(value) ? this : empty();
    }

    public <U> Container<U> map(Function<? super T, ? extends U> mapper) {
        Objects.requireNonNull(mapper);
        if (isEmpty()) return empty();
        return Container.ofNullable(mapper.apply(value));
    }

    public <U> Container<U> flatMap(Function<? super T, ? extends Container<? extends U>> mapper) {
        Objects.requireNonNull(mapper);
        if (isEmpty()) return empty();
        //noinspection unchecked
        return Objects.requireNonNull((Container<U>) mapper.apply(value));
    }

    public Container<T> or(Supplier<? extends Container<? extends T>> supplier) {
        Objects.requireNonNull(supplier);
        if (isPresent()) return this;
        @SuppressWarnings("unchecked")
        Container<T> r = (Container<T>) supplier.get();
        return Objects.requireNonNull(r);
    }

    public Stream<T> stream() {
        if (isEmpty()) return Stream.empty();
        return Stream.of(value);
    }

    public T orElse(T other) {
        return value != null ? value : other;
    }

    public T orElseGet(Supplier<? extends T> supplier) {
        return value != null ? value : supplier.get();
    }

    public T orElseThrow() {
        if (value == null) throw new NoSuchElementException("No value present");
        return value;
    }

    public <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
        if (value != null) return value;
        else throw exceptionSupplier.get();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        return obj instanceof Container<?> other
                && Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return value != null
                ? ("Container[" + value + "]")
                : "Container.empty";
    }

}
