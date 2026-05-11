package dev.sweety.event.api;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public abstract class AbstractEvent<E extends Event<E>> implements Event<E> {

    protected volatile boolean changed = false;
    protected boolean pre = true;

    @Override
    @NotNull
    public E post() {
        this.pre = false;
        //noinspection unchecked
        return (E) this;
    }

    @Override
    public boolean isPost() {
        return !this.pre;
    }

    @Override
    public boolean isPre() {
        return pre;
    }

    @Override
    public boolean isChanged() {
        return changed;
    }

    public void setChanged(boolean changed) {
        this.changed = changed;
    }

    @NotNull
    public E toImmutable() {
        //noinspection unchecked
        return (E) this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AbstractEvent<?> that)) return false;
        return changed == that.changed && pre == that.pre;
    }

    @Override
    public int hashCode() {
        return Objects.hash(changed, pre);
    }
}
